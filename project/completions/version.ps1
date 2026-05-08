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

    $flags = @(
        '-r', '--repository',
        '-b', '--basis-commit',
        '--pr',
        '--branch-override',
        '--sha-length',
        '-v', '--verbose',
        '--ci',
        '--no-colour', '--no-color',
        '-e', '--emit',
        '--console-style',
        '--help', '--version'
    )

    $sinks  = @('console', 'raw', 'json')
    $styles = @('pretty', 'compact')

    $tokens = $commandAst.CommandElements
    $previous = ''
    for ($i = $tokens.Count - 1; $i -ge 0; $i--) {
        $text = $tokens[$i].Extent.Text
        if ($text -ne $wordToComplete) { $previous = $text; break }
    }

    switch ($previous) {
        { $_ -in @('-e', '--emit') } {
            $sinks | Where-Object { $_ -like "$wordToComplete*" } |
                ForEach-Object { [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_) }
            return
        }
        '--console-style' {
            $styles | Where-Object { $_ -like "$wordToComplete*" } |
                ForEach-Object { [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_) }
            return
        }
    }

    $flags | Where-Object { $_ -like "$wordToComplete*" } |
        ForEach-Object { [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterName', $_) }
}
