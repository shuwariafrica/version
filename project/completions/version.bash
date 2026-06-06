# bash completion for the `version` CLI
#
# Install:
#   cp version.bash /etc/bash_completion.d/version
#   # or, for a single user:
#   cp version.bash ~/.local/share/bash-completion/completions/version
#
# Alpine/musl: ensure `bash-completion` is installed.

_version() {
    local cur prev cmd i
    COMPREPLY=()
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"

    local commands="target bump tag list"
    local keywords="breaking feat feature fix major minor patch"
    local sinks="console raw json"
    local styles="pretty compact"
    local globals="-r --repository -b --basis-commit --pr --branch-override --sha-length \
-v --verbose --ci --no-colour --no-color -e --emit --console-style --help --version"

    # Complete the value for an option that takes one.
    case "${prev}" in
        -r|--repository) COMPREPLY=( $(compgen -d -- "${cur}") ); return ;;
        -e|--emit)       COMPREPLY=( $(compgen -W "${sinks}" -- "${cur}") ); return ;;
        --console-style) COMPREPLY=( $(compgen -W "${styles}" -- "${cur}") ); return ;;
        -b|--basis-commit|--pr|--branch-override|--sha-length|-m|--message|-n|--limit|--since|--until)
            return ;;
    esac

    # Identify the selected subcommand, if any.
    cmd=""
    for (( i = 1; i < COMP_CWORD; i++ )); do
        case "${COMP_WORDS[i]}" in
            target|bump|tag|list) cmd="${COMP_WORDS[i]}"; break ;;
        esac
    done

    # A bare bump argument completes to a scheme keyword.
    if [[ "${cmd}" == "bump" && "${cur}" != -* ]]; then
        COMPREPLY=( $(compgen -W "${keywords}" -- "${cur}") ); return
    fi

    # At the top level with no subcommand yet, offer the subcommands.
    if [[ -z "${cmd}" && "${cur}" != -* ]]; then
        COMPREPLY=( $(compgen -W "${commands}" -- "${cur}") ); return
    fi

    # Otherwise offer options: the command's own, then the globals.
    local opts="${globals}"
    case "${cmd}" in
        target) opts="--dry-run --no-sign ${globals}" ;;
        bump)   opts="--dry-run --no-sign ${globals}" ;;
        tag)    opts="-m --message --no-sign --dry-run ${globals}" ;;
        list)   opts="-n --limit --final --since --until --details ${globals}" ;;
    esac
    COMPREPLY=( $(compgen -W "${opts}" -- "${cur}") )
}

complete -F _version version
