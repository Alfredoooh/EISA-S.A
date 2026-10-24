from pathlib import Path
import re

base = Path('/mnt/data/work_v20/src/main/res/layout')
# Screen titles, section headings, content headlines and list-row titles that should match
# the lighter medium-weight typography used by the History toolbar.
ids_by_file = {
    'activity_main.xml': {'homeQuickTitle', 'homeNewsTitle', 'feedToolbarTitle'},
    'activity_settings.xml': {'setTitle'},
    'activity_categories.xml': set(),
    'activity_usage.xml': set(),
    'activity_data_saver.xml': set(),
    'activity_library.xml': set(),
    'activity_search.xml': {'searchModeTitle'},
    'activity_comments.xml': set(),
    'activity_article.xml': {'aTitle'},
    'item_news.xml': {'title'},
    'item_top_cards.xml': {'topSavingsTitle', 'topHistoryTitle'},
    'row_setting.xml': {'rowTitle'},
    'sheet_categories.xml': set(),
    'sheet_reactions.xml': set(),
}
texts_by_file = {
    'activity_categories.xml': {'Categorias'},
    'activity_usage.xml': {'Atividade de uso', 'Fontes de notícias mais visitadas', 'Fontes visitadas hoje'},
    'activity_data_saver.xml': {'Poupança de dados', 'Desativar reprodução de vídeos', 'Reduzir qualidade das imagens'},
    'activity_library.xml': {'Biblioteca', 'Nada guardado ainda'},
    'activity_comments.xml': {'Comentários'},
    'activity_article.xml': {'Comentários'},
    'sheet_categories.xml': {'Categoria'},
    'sheet_reactions.xml': {'Reagir com'},
}

def lighten_element(tag: str) -> str:
    # Do not touch numeric badges, navigation selectors or prominent submit buttons.
    tag = re.sub(r'\s+android:textStyle="bold"', ' android:textStyle="normal"', tag)
    if 'android:fontFamily=' not in tag:
        # Insert alongside the remaining attributes, preserving the XML element shape.
        pos = tag.rfind('/>')
        if pos >= 0:
            tag = tag[:pos] + ' android:fontFamily="sans-serif-medium"' + tag[pos:]
        else:
            pos = tag.rfind('>')
            tag = tag[:pos] + ' android:fontFamily="sans-serif-medium"' + tag[pos:]
    return tag

for filename in ids_by_file:
    p = base / filename
    src = p.read_text(encoding='utf-8')
    ids = ids_by_file[filename]
    texts = texts_by_file.get(filename, set())
    # Target individual TextView elements only, preserving surrounding XML formatting.
    pattern = re.compile(r'<TextView\b[^>]*(?:/>|>.*?</TextView>)', re.S)
    def repl(m):
        tag = m.group(0)
        id_match = re.search(r'android:id="@\+?id/([^" ]+)"', tag)
        id_name = id_match.group(1) if id_match else None
        text_match = re.search(r'android:text="([^"]*)"', tag)
        text_value = text_match.group(1) if text_match else None
        if (id_name in ids) or (text_value in texts):
            return lighten_element(tag)
        return tag
    updated = pattern.sub(repl, src)
    p.write_text(updated, encoding='utf-8')

# Lower programmatically-created semantic titles/headlines to the same medium weight.
def replace_file(rel, pairs):
    p = Path('/mnt/data/work_v20/src/main/java') / rel
    s = p.read_text(encoding='utf-8')
    original = s
    for old, new in pairs:
        if old not in s:
            print(f'WARNING: pattern not found in {rel}: {old[:70]!r}')
        s = s.replace(old, new)
    p.write_text(s, encoding='utf-8')
    print(f'updated {rel}: {s != original}')

replace_file('com/appao/news/NewsAdapter.kt', [
    ('Typeface.create(\n                    "sans-serif",\n                    Typeface.BOLD\n                )', 'Typeface.create(\n                    "sans-serif-medium",\n                    Typeface.NORMAL\n                )'),
])
replace_file('com/appao/screens/CategoryManagerActivity.kt', [
    ('setTypeface(typeface, android.graphics.Typeface.BOLD)', 'setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))'),
])
replace_file('com/appao/screens/BrowserTabsActivity.kt', [
    ('setTypeface(typeface, android.graphics.Typeface.BOLD)', 'setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))'),
])
replace_file('com/appao/ui/NativeSheetDialog.kt', [
    ('android.graphics.Typeface.DEFAULT,\n                    android.graphics.Typeface.BOLD', 'android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)'),
])
replace_file('com/appao/screens/ArticleActivity.kt', [
    ('setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)', 'setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))'),
    ('android.graphics.Typeface.DEFAULT,\n                    android.graphics.Typeface.BOLD', 'android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)'),
])
