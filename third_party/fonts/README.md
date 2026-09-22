# Bundled fonts

| Family | Files | Licence | Source |
|---|---|---|---|
| Libre Franklin (Light, Regular, Medium, SemiBold) | `core-render/src/main/resources/fonts/libre_franklin_*.ttf`, `app/src/main/res/font/libre_franklin_*.ttf` | SIL OFL 1.1 — [OFL-LibreFranklin.txt](OFL-LibreFranklin.txt) | [googlefonts/Libre-Franklin](https://github.com/googlefonts/Libre-Franklin) |
| Libre Caslon Text (Regular, Bold) | `core-render/src/main/resources/fonts/libre_caslon_text_*.ttf`, `app/src/main/res/font/libre_caslon_text_regular.ttf` | SIL OFL 1.1 — [OFL-LibreCaslonText.txt](OFL-LibreCaslonText.txt) | [impallari/Libre-Caslon-Text](https://github.com/impallari/Libre-Caslon-Text) |

The TTFs are the static instances Google Fonts serves (Libre Franklin v20, Libre Caslon Text v5).
The PDF embeds subsets of them; the app uses them for the phone UI. Neither family has glyphs
for arrows (U+2190/2192) or check marks (U+2713): the PDF avoids those characters, and on the
phone Android falls back to a system font for them.
