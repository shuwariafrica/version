#include <git2.h>
#include <stdlib.h>

/* --------------------------------------------------------------------------
 * working-tree dirty count
 * -------------------------------------------------------------------------- */

/**
 * Returns the number of working-tree status entries representing a deviation from HEAD
 * (modified, untracked, or new index entries; ignored files are excluded). Returns 0 when
 * the working tree is clean. Returns -1 on libgit2 failure.
 *
 * Implemented as a single shim so that the git_status_options struct is stack-allocated
 * and properly initialised via GIT_STATUS_OPTIONS_INIT — heap-allocating a
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
 * git_error
 * -------------------------------------------------------------------------- */

/**
 * Read the message field from a git_error.
 * Returns NULL if err is NULL.
 */
const char* version_resolution_git_error_message(const git_error* err) {
    return err ? err->message : NULL;
}

/**
 * Read the klass field from a git_error.
 * Returns 0 if err is NULL.
 */
int version_resolution_git_error_klass(const git_error* err) {
    return err ? err->klass : 0;
}

/* --------------------------------------------------------------------------
 * git_repository_is_bare
 * -------------------------------------------------------------------------- */

/**
 * Thin wrapper: git_repository_is_bare returns non-zero if bare.
 */
int version_resolution_git_repository_is_bare(git_repository* repo) {
    return git_repository_is_bare(repo);
}

/* --------------------------------------------------------------------------
 * git_commit_time
 * -------------------------------------------------------------------------- */

/**
 * Thin wrapper: git_commit_time returns git_time_t (int64_t).
 * We return as int for the RawCommit.commitTime field (seconds since epoch,
 * matching JGit RevCommit.getCommitTime() which returns int).
 */
int version_resolution_git_commit_time(const git_commit* commit) {
    return (int)git_commit_time(commit);
}

/* --------------------------------------------------------------------------
 * git_commit_message_encoding
 * -------------------------------------------------------------------------- */

/**
 * Returns the encoding of the commit message, or NULL for UTF-8 default.
 */
const char* version_resolution_git_commit_message_encoding(const git_commit* commit) {
    return git_commit_message_encoding(commit);
}
