# Local development preferences

- Work on DEV. Make small local commits with English messages; do not push or publish.
- Keep all new project code, UI text and documentation in English.
- Keep the release version at **0.4.0**, including future updates, unless the user explicitly changes this instruction.
- Deploy every finished, checked mod build to `C:\Users\water\AppData\Roaming\ModrinthApp\profiles\NeoForge 1.21.1\mods` using `tools/deploy-test-build.ps1`. Back up the previous WGE jar before replacement. Do not modify other mods or user saves.
- Preserve pre-existing user edits, including tracked `common/bin/` changes. Stage explicit source paths, not generated IDE files.
- The companion preview repository is `C:\Programming\IdeaProjects\WorldGen Editor Preview`. Keep shared document formats compatible and commit separately there.
