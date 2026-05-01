# bash completion for the `version` CLI
#
# Install:
#   cp version.bash /etc/bash_completion.d/version
#   # or, for a single user:
#   cp version.bash ~/.local/share/bash-completion/completions/version
#
# Alpine/musl: ensure `bash-completion` is installed.

_version() {
    local cur prev opts sinks styles
    COMPREPLY=()
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"

    opts="-r --repository -b --basis-commit --pr --branch-override --sha-length \
          -v --verbose --ci --no-colour --no-color \
          -e --emit --console-style \
          --help --version"
    sinks="console raw json"
    styles="pretty compact"

    case "${prev}" in
        -r|--repository)
            COMPREPLY=( $(compgen -d -- "${cur}") )
            return 0
            ;;
        -e|--emit)
            COMPREPLY=( $(compgen -W "${sinks}" -- "${cur}") )
            return 0
            ;;
        --console-style)
            COMPREPLY=( $(compgen -W "${styles}" -- "${cur}") )
            return 0
            ;;
        -b|--basis-commit|--pr|--branch-override|--sha-length)
            return 0
            ;;
    esac

    if [[ ${cur} == -* ]]; then
        COMPREPLY=( $(compgen -W "${opts}" -- "${cur}") )
        return 0
    fi
}

complete -F _version version
