# fish completion for the `version` CLI
#
# Install:
#   cp version.fish ~/.config/fish/completions/version.fish

complete -c version -s r -l repository -r -d 'Repository directory' -F
complete -c version -s b -l basis-commit -r -d 'Commit-ish to resolve against'
complete -c version       -l pr              -r -d 'Pull request number'
complete -c version       -l branch-override -r -d 'Override branch name'
complete -c version       -l sha-length      -r -d 'Abbreviated SHA length (7..40)'
complete -c version -s v -l verbose             -d 'Enable verbose diagnostics'
complete -c version       -l ci                 -d 'CI mode'
complete -c version       -l no-colour          -d 'Disable ANSI colours'
complete -c version       -l no-color           -d 'Disable ANSI colours'
complete -c version -s e -l emit -r             -d 'Add output sink' -xa 'console raw json'
complete -c version       -l console-style -r   -d 'Console rendering style' -xa 'pretty compact'
complete -c version       -l help               -d 'Print help'
complete -c version       -l version            -d 'Print version'
