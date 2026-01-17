# VerbSpiel

Android app for practicing German separable verbs by matching prefixes and roots.
Built mostly by codex.

## Features
- Practice rounds with prefix/root pickers
- Word statistics (recent correct/failures, top words, retired, favorites)
- Favorites and learned marks
- Optional filters (prefix/root/favorites)
- Room database with stats tracking and word sync

## Project Structure
- `app/src/main/java/com/verbspiel` - Kotlin source
- `app/src/main/res` - layouts, strings, and `raw/data.txt` word list

## Word List Format
`app/src/main/res/raw/data.txt`:
```
version:3
prefix;root;translation;example
an;kommen;to arrive;Der Zug kommt in fünf Minuten an.
an;ziehen|(sich);to get dressed;Ich ziehe mich morgens schnell an.
```
- Reflexive verbs use `|(sich)` in the root field.
- Multiple meanings can be added by repeating the same `prefix;root` with different translations.

## License
MIT. See `LICENSE`.
