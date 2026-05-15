#include <git2.h>

/* --------------------------------------------------------------------------
 * working-tree dirty count
 * -------------------------------------------------------------------------- */

/**
 * Returns the number of working-tree status entries representing a deviation from HEAD
 * (modified, untracked, or new index entries; ignored files are excluded). Returns 0 when
 * the working tree is clean. Returns -1 on libgit2 failure.
 *
 * Implemented as a single shim so that the git_status_options struct is stack-allocated
 * and properly initialised via GIT_STATUS_OPTIONS_INIT - heap-allocating a
 * git_status_options across the FFI boundary triggers a segfault inside
 * git_diff_index_to_workdir on musl/Alpine, even when the struct is otherwise valid.
 */
int version_resolution_git_workdir_dirty_count(git_repository* repo) {
    if (git_repository_is_bare(repo)) {
        return 0;
    }
    git_status_options opts = GIT_STATUS_OPTIONS_INIT;
    opts.show  = GIT_STATUS_SHOW_INDEX_AND_WORKDIR;
    opts.flags = GIT_STATUS_OPT_INCLUDE_UNTRACKED
               | GIT_STATUS_OPT_RECURSE_UNTRACKED_DIRS
               | GIT_STATUS_OPT_EXCLUDE_SUBMODULES;
    git_status_list* list = NULL;
    int rc = git_status_list_new(&list, repo, &opts);
    if (rc < 0) {
        return -1;
    }
    size_t count = git_status_list_entrycount(list);
    git_status_list_free(list);
    return (int)count;
}

/* --------------------------------------------------------------------------
 * git_error message accessor
 * -------------------------------------------------------------------------- */

/* Read the message field from a git_error; NULL if err is NULL. The shim
 * keeps the git_error struct shape out of the Scala FFI surface. */
const char* version_resolution_git_error_message(const git_error* err) {
    return err ? err->message : NULL;
}

/* TODO: drop this whole section once scala-native ships the
 * main-thread maxStackSize fix in nativeThreadTLS.c::detectStackBounds. */

#ifdef __linux__

#define _GNU_SOURCE 1
#include <stdint.h>
#include <stdio.h>
#include <stdbool.h>
#include <sys/resource.h>
#include <unistd.h>

/* Layout must match scala-native's ThreadInfo (nativeThreadTLS.h) through
 * maxStackSize so we can patch that field in place. */
typedef struct SnThreadInfo {
    size_t stackSize;
    size_t maxStackSize;
    void *stackTop;
    void *stackBottom;
    void *stackGuardPage;
    bool isMainThread;
    bool pendingStackOverflowException;
    void *signalHandlerStack;
    size_t signalHandlerStackSize;
} SnThreadInfo;

extern SnThreadInfo *scalanative_currentThreadInfo(void);

/* Restore the main thread's maxStackSize to RLIMIT_STACK; scala-native's
 * detectStackBounds clobbers it with pthread_attr_getstack's initial-mapping
 * size on Linux. Idempotent. */
int version_resolution_fix_main_thread_stack_limit(void) {
    SnThreadInfo *ti = scalanative_currentThreadInfo();
    if (ti == NULL) return 0;
    if (!ti->isMainThread) return 0;

    struct rlimit rl;
    if (getrlimit(RLIMIT_STACK, &rl) != 0) return 0;
    if (rl.rlim_cur == RLIM_INFINITY) return 0;

    long pageSize = sysconf(_SC_PAGESIZE);
    if (pageSize <= 0) return 0;

    /* rlimit minus 4 guard pages - mirror scalanative_setupCurrentThreadInfo. */
    if ((size_t)rl.rlim_cur <= (size_t)(4 * pageSize)) return 0;
    size_t correctMax = (size_t)rl.rlim_cur - (size_t)(4 * pageSize);
    if (correctMax > ti->maxStackSize) {
        ti->maxStackSize = correctMax;
        return 1;
    }
    return 0;
}

#else  /* non-Linux: pthread_attr_getstack returns the actual stack size, no
        * patching needed. Provide a stub so the Scala extern resolves. */

int version_resolution_fix_main_thread_stack_limit(void) { return 0; }

#endif
