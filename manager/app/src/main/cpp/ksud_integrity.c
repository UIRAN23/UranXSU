#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include "ksu.h"
#include "prelude.h"

#define KSUD_PATH "/data/adb/ksud"
#define KSUD_TMP_PATH "/data/adb/ksud.tmp"
#define KSU_BIN_DIR "/data/adb/ksu/bin"
#define KSU_BIN_KSUD "/data/adb/ksu/bin/ksud"
#define KSU_BIN_RESETPROP "/data/adb/ksu/bin/resetprop"

#define RC_UP_TO_DATE 0
#define RC_NEEDS_SYNC 1
#define RC_MISSING 2
#define RC_ERR_GRANT_ROOT 10
#define RC_ERR_SRC_OPEN 11
#define RC_ERR_WRITE 12

static int read_full(int fd, uint8_t *buf, size_t len) {
    size_t total = 0;
    while (total < len) {
        ssize_t n = TEMP_FAILURE_RETRY(read(fd, buf + total, len - total));
        if (n < 0) return -1;
        if (n == 0) break;
        total += (size_t)n;
    }
    return (int)total;
}

static bool write_full(int fd, const uint8_t *buf, size_t len) {
    size_t total = 0;
    while (total < len) {
        ssize_t n = TEMP_FAILURE_RETRY(write(fd, buf + total, len - total));
        if (n <= 0) return false;
        total += (size_t)n;
    }
    return true;
}

static int compare_files(const char *src_path, const char *dst_path) {
    struct stat src_st;
    struct stat dst_st;
    if (stat(src_path, &src_st) != 0 || !S_ISREG(src_st.st_mode)) {
        return RC_ERR_SRC_OPEN;
    }
    if (stat(dst_path, &dst_st) != 0) {
        return (errno == ENOENT) ? RC_MISSING : RC_NEEDS_SYNC;
    }
    if (!S_ISREG(dst_st.st_mode) || src_st.st_size != dst_st.st_size) {
        return RC_NEEDS_SYNC;
    }

    int src_fd = open(src_path, O_RDONLY | O_CLOEXEC);
    if (src_fd < 0) return RC_ERR_SRC_OPEN;

    int dst_fd = open(dst_path, O_RDONLY | O_CLOEXEC);
    if (dst_fd < 0) {
        close(src_fd);
        return (errno == ENOENT) ? RC_MISSING : RC_NEEDS_SYNC;
    }

    constexpr size_t kChunk = 65536;
    uint8_t *buf_a = malloc(kChunk);
    uint8_t *buf_b = malloc(kChunk);
    if (!buf_a || !buf_b) {
        free(buf_a);
        free(buf_b);
        close(src_fd);
        close(dst_fd);
        return RC_ERR_WRITE;
    }

    int result = RC_UP_TO_DATE;
    while (true) {
        int na = read_full(src_fd, buf_a, kChunk);
        int nb = read_full(dst_fd, buf_b, kChunk);
        if (na < 0 || nb < 0 || na != nb) {
            result = RC_NEEDS_SYNC;
            break;
        }
        if (na == 0) break;
        if (memcmp(buf_a, buf_b, (size_t)na) != 0) {
            result = RC_NEEDS_SYNC;
            break;
        }
    }

    free(buf_a);
    free(buf_b);
    close(src_fd);
    close(dst_fd);
    return result;
}

static void ensure_dir_0755(const char *path) {
    struct stat st;
    if (stat(path, &st) == 0 && S_ISDIR(st.st_mode)) return;
    mkdir(path, 0755);
}

static void repair_symlink(const char *link_path, const char *target) {
    char existing[PATH_MAX];
    ssize_t n = readlink(link_path, existing, sizeof(existing) - 1);
    if (n > 0) {
        existing[n] = '\0';
        if (strcmp(existing, target) == 0) {
            return;
        }
    }
    unlink(link_path);
    symlink(target, link_path);
}

static int child_repair_ksud(const char *src_path) {
    ensure_dir_0755("/data/adb");
    ensure_dir_0755("/data/adb/ksu");
    ensure_dir_0755(KSU_BIN_DIR);

    int cmp = compare_files(src_path, KSUD_PATH);
    if (cmp == RC_ERR_SRC_OPEN) {
        return RC_ERR_SRC_OPEN;
    }
    if (cmp != RC_UP_TO_DATE) {
        int src_fd = open(src_path, O_RDONLY | O_CLOEXEC);
        if (src_fd < 0) return RC_ERR_SRC_OPEN;

        int dst_fd = open(KSUD_TMP_PATH, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0755);
        if (dst_fd < 0) {
            close(src_fd);
            return RC_ERR_WRITE;
        }

        constexpr size_t kChunk = 65536;
        uint8_t *buf = malloc(kChunk);
        if (!buf) {
            close(src_fd);
            close(dst_fd);
            unlink(KSUD_TMP_PATH);
            return RC_ERR_WRITE;
        }

        bool ok = true;
        while (true) {
            ssize_t n = TEMP_FAILURE_RETRY(read(src_fd, buf, kChunk));
            if (n < 0) {
                ok = false;
                break;
            }
            if (n == 0) break;
            if (!write_full(dst_fd, buf, (size_t)n)) {
                ok = false;
                break;
            }
        }
        free(buf);
        close(src_fd);

        if (!ok || fchmod(dst_fd, 0755) != 0 || fsync(dst_fd) != 0) {
            close(dst_fd);
            unlink(KSUD_TMP_PATH);
            return RC_ERR_WRITE;
        }
        (void)fchown(dst_fd, 0, 0);
        close(dst_fd);

        if (rename(KSUD_TMP_PATH, KSUD_PATH) != 0) {
            unlink(KSUD_TMP_PATH);
            return RC_ERR_WRITE;
        }
    }

    // Only repair ksud and resetprop symlinks; leave standalone tools & ksu_susfs untouched.
    repair_symlink(KSU_BIN_KSUD, KSUD_PATH);
    repair_symlink(KSU_BIN_RESETPROP, KSUD_PATH);
    return RC_UP_TO_DATE;
}

static int run_ksud_op_in_child(const char *src_path, bool repair) {
    if (!src_path || !src_path[0]) return -2;

    pid_t pid = fork();
    if (pid < 0) return -3;
    if (pid == 0) {
        if (getuid() != 0 && !ksu_grant_root()) {
            _exit(RC_ERR_GRANT_ROOT);
        }
        int rc = repair ? child_repair_ksud(src_path) : compare_files(src_path, KSUD_PATH);
        _exit(rc);
    }

    int status = 0;
    if (TEMP_FAILURE_RETRY(waitpid(pid, &status, 0)) < 0) {
        return -3;
    }
    if (!WIFEXITED(status)) {
        return -4;
    }
    int code = WEXITSTATUS(status);
    switch (code) {
        case RC_UP_TO_DATE:
        case RC_NEEDS_SYNC:
        case RC_MISSING:
            return code;
        case RC_ERR_GRANT_ROOT:
            return -1;
        case RC_ERR_SRC_OPEN:
            return -2;
        case RC_ERR_WRITE:
            return -3;
        default:
            return -4;
    }
}

int ksu_check_ksud_integrity(const char *apk_ksud_path) {
    return run_ksud_op_in_child(apk_ksud_path, false);
}

int ksu_repair_ksud(const char *apk_ksud_path) {
    return run_ksud_op_in_child(apk_ksud_path, true);
}
