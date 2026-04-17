#include <git2.h>
#include <stdlib.h>

/* --------------------------------------------------------------------------
 * git_status_options
 * -------------------------------------------------------------------------- */

/**
 * Allocate and initialise a git_status_options configured for a
 * "working directory clean?" check.
 *
 * Returns NULL if allocation fails.
 * Caller must free via version_resolution_git_status_options_free.
 */
git_status_options* version_resolution_git_status_options_new(void) {
    git_status_options* opts = (git_status_options*)malloc(sizeof(git_status_options));
    if (opts == NULL) {
        return NULL;
    }
    if (git_status_options_init(opts, GIT_STATUS_OPTIONS_VERSION) < 0) {
        free(opts);
        return NULL;
    }
    opts->show  = GIT_STATUS_SHOW_INDEX_AND_WORKDIR;
    opts->flags = GIT_STATUS_OPT_INCLUDE_UNTRACKED
                | GIT_STATUS_OPT_RECURSE_UNTRACKED_DIRS
                | GIT_STATUS_OPT_EXCLUDE_SUBMODULES;
    return opts;
}

void version_resolution_git_status_options_free(git_status_options* opts) {
    free(opts);
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
