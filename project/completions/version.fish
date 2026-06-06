# fish completion for the `version` CLI
#
# Install:
#   cp version.fish ~/.config/fish/completions/version.fish

# Arguments are subcommands or version strings, never files - only --repository re-enables file completion.
complete -c version -f

# Subcommands, offered only at the top level.
complete -c version -f -n __fish_use_subcommand -a target  -d 'Show the resolution target, or commit a target directive'
complete -c version -f -n __fish_use_subcommand -a bump    -d 'Commit a version bump directive'
complete -c version -f -n __fish_use_subcommand -a tag     -d 'Create an annotated tag at HEAD'
complete -c version -f -n __fish_use_subcommand -a list    -d 'List the release history'

# bump <keyword>
complete -c version -f -n '__fish_seen_subcommand_from bump' -a 'breaking feat feature fix major minor patch'
complete -c version    -n '__fish_seen_subcommand_from bump' -l dry-run -d 'Preview without committing'
complete -c version    -n '__fish_seen_subcommand_from bump' -l no-sign -d 'Create the commit unsigned'

# target [version]
complete -c version    -n '__fish_seen_subcommand_from target' -l dry-run -d 'Preview without committing'
complete -c version    -n '__fish_seen_subcommand_from target' -l no-sign -d 'Create the commit unsigned'

# tag [version]
complete -c version    -n '__fish_seen_subcommand_from tag' -s m -l message -r -d 'Tag message'
complete -c version    -n '__fish_seen_subcommand_from tag' -l no-sign -d 'Create the tag unsigned'
complete -c version    -n '__fish_seen_subcommand_from tag' -l dry-run -d 'Preview without tagging'

# list
complete -c version    -n '__fish_seen_subcommand_from list' -s n -l limit -r -d 'Limit to the newest N entries'
complete -c version    -n '__fish_seen_subcommand_from list' -l final   -d 'Exclude pre-releases'
complete -c version    -n '__fish_seen_subcommand_from list' -l since -r -d 'Releases at or above VERSION'
complete -c version    -n '__fish_seen_subcommand_from list' -l until -r -d 'Releases at or below VERSION'
complete -c version    -n '__fish_seen_subcommand_from list' -l details -d 'Show the tag and source-commit date'

# Global options, valid throughout.
complete -c version -s r -l repository      -r -F -d 'Repository directory'
complete -c version -s b -l basis-commit    -r    -d 'Commit-ish to resolve against'
complete -c version      -l pr              -r    -d 'Pull request number'
complete -c version      -l branch-override -r    -d 'Override branch name'
complete -c version      -l sha-length      -r    -d 'SHA length for the full renderer (7..64)'
complete -c version -s v -l verbose               -d 'Enable verbose diagnostics'
complete -c version      -l ci                    -d 'CI mode'
complete -c version      -l no-colour             -d 'Disable ANSI colours'
complete -c version      -l no-color              -d 'Disable ANSI colours'
complete -c version -s e -l emit         -r -xa 'console raw json' -d 'Add output sink'
complete -c version      -l console-style -r -xa 'pretty compact'  -d 'Console rendering style'
complete -c version      -l help                  -d 'Print help'
complete -c version      -l version               -d 'Print version'
