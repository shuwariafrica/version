# PowerShell completion for the `version` CLI
#
# Install (current user):
#   # Append the contents of this file to $PROFILE, or dot-source it from your profile:
#   #   . "$HOME\Documents\PowerShell\Modules\version\version.ps1"
#
# System-wide installation follows standard PowerShell module layout
# conventions; see about_Profiles for details.

Register-ArgumentCompleter -Native -CommandName version -ScriptBlock {
    param($wordToComplete, $commandAst, $cursorPosition)

    $commands = @('target', 'tag', 'list')
    $keywords = @('breaking', 'feat', 'feature', 'fix', 'major', 'minor', 'patch')
    $sinks    = @('console', 'raw', 'json')
    $styles   = @('pretty', 'compact')
    $globals  = @(
        '-r', '--repository', '-b', '--basis-commit', '--pr', '--branch-override',
        '--sha-length', '-v', '--verbose', '--ci', '--no-colour', '--no-color',
        '-e', '--emit', '--console-style', '--help', '--version'
    )
    $perCommand = @{
        target  = @('-s', '--set', '-i', '--increment', '--dry-run', '--no-sign')
        tag     = @('-m', '--message', '--no-sign', '--dry-run')
        list    = @('-n', '--limit', '--final', '--since', '--until', '--details')
    }
    $valueOptions = @(
        '-r', '--repository', '-b', '--basis-commit', '--pr', '--branch-override',
        '--sha-length', '-m', '--message', '-s', '--set', '-n', '--limit', '--since', '--until'
    )

    # Scan the typed elements for the selected subcommand and the token left of the cursor.
    $elements = $commandAst.CommandElements
    $command  = $null
    $previous = ''
    for ($i = 1; $i -lt $elements.Count; $i++) {
        $text = $elements[$i].Extent.Text
        if ($text -eq $wordToComplete) { continue }
        $previous = $text
        if ((-not $command) -and ($commands -contains $text)) { $command = $text }
    }

    $candidates = $null
    $resultType = 'ParameterValue'
    if ($previous -eq '-e' -or $previous -eq '--emit') {
        $candidates = $sinks
    }
    elseif ($previous -eq '--console-style') {
        $candidates = $styles
    }
    elseif ($previous -eq '-i' -or $previous -eq '--increment') {
        $candidates = $keywords
    }
    elseif ($valueOptions -contains $previous) {
        return  # the previous option takes a free value (path, rev, number, message, version); defer to default completion
    }
    elseif ($wordToComplete -like '-*') {
        $candidates = if ($command) { $perCommand[$command] + $globals } else { $globals }
        $resultType = 'ParameterName'
    }
    elseif (-not $command) {
        $candidates = $commands
    }
    else {
        # A chosen subcommand at a free-argument position (e.g. a version string): offer its options, not files.
        $candidates = $perCommand[$command] + $globals
        $resultType = 'ParameterName'
    }

    $candidates | Where-Object { $_ -like "$wordToComplete*" } | ForEach-Object {
        [System.Management.Automation.CompletionResult]::new($_, $_, $resultType, $_)
    }
}
