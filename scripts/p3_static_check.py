#!/usr/bin/env python3
"""
P3WIN static checker — замена компилятору в среде без JDK.

Проверяет:
  1. Синтаксис всех Java-файлов (javalang).
  2. Импорты com.winlator.* — тип существует как файл и public.
  3. Обращения к ядру: метод/поле реально объявлены (с учётом extends).
  4. Ссылки R.id / R.layout / R.string / R.drawable / R.style ... существуют.
  5. XML well-formed (xml.etree).
  6. Запрещённые конструкции: хардкод /data/data/com.winlator, if (false).
  7. Слойность: ядро не импортирует com.p3win.

Запуск: python3 scripts/p3_static_check.py
Код возврата: 0 — чисто, 1 — есть ошибки.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_ROOT = os.path.join(ROOT, "app/src/main/java")
RES_ROOT = os.path.join(ROOT, "app/src/main/res")
MAIN_ROOT = os.path.join(ROOT, "app/src/main")
P3_ROOT = os.path.join(JAVA_ROOT, "com/p3win")
CORE_ROOT = os.path.join(JAVA_ROOT, "com/winlator")

errors = []
warnings = []


def err(m):
    errors.append(m)


def warn(m):
    warnings.append(m)


def rel(p):
    return os.path.relpath(p, ROOT)


def strip_comments(text):
    """Убрать // и /* */ комментарии: упоминание R.id в javadoc — не использование."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


# --- 1. syntax -------------------------------------------------------------
def check_syntax():
    try:
        import javalang
    except ImportError:
        warn("javalang не установлен — синтаксис не проверен")
        return
    n = 0
    for dp, _, fs in os.walk(JAVA_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            n += 1
            try:
                javalang.parse.parse(open(p, encoding="utf-8").read())
            except Exception as e:
                err(f"SYNTAX {rel(p)}: {type(e).__name__}: {str(e)[:180]}")
    print(f"[syntax] {n} Java-файлов")


# --- 2. imports ------------------------------------------------------------
def core_types():
    types = {}
    for dp, _, fs in os.walk(CORE_ROOT):
        for f in fs:
            if f.endswith(".java"):
                p = os.path.join(dp, f)
                types[os.path.relpath(p, JAVA_ROOT)[:-5].replace(os.sep, ".")] = p
    return types


def check_imports():
    types = core_types()
    rx = re.compile(r"^import\s+(static\s+)?(com\.winlator\.[\w.]+);", re.M)
    n = 0
    for dp, _, fs in os.walk(P3_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            text = open(p, encoding="utf-8").read()
            for m in rx.finditer(text):
                fq = m.group(2)
                if m.group(1):
                    fq = fq.rsplit(".", 1)[0]
                n += 1
                # com.winlator.R генерируется AAPT из res/ — файла в src нет.
                if fq == "com.winlator.R" or fq.startswith("com.winlator.R."):
                    continue
                if fq in types:
                    continue
                if fq.rsplit(".", 1)[0] in types:
                    continue
                err(f"IMPORT {rel(p)}: нет типа {fq}")
    print(f"[imports] {n} импортов com.winlator.*")


# --- 3. resources ----------------------------------------------------------
def index_resources():
    idx = {k: set() for k in
           ("id", "layout", "string", "drawable", "style", "color", "xml", "array",
            "dimen", "bool", "integer", "menu", "mipmap", "anim", "attr")}
    for dp, _, fs in os.walk(RES_ROOT):
        folder = os.path.basename(dp)
        for f in fs:
            p = os.path.join(dp, f)
            stem = f.rsplit(".", 1)[0]
            if folder.startswith("layout"):
                idx["layout"].add(stem)
            elif folder.startswith("menu"):
                idx["menu"].add(stem)
            elif folder == "xml":
                idx["xml"].add(stem)
            elif folder.startswith("drawable"):
                idx["drawable"].add(stem)
            elif folder.startswith("mipmap"):
                idx["mipmap"].add(stem)
            elif folder.startswith("anim"):
                idx["anim"].add(stem)
            if not f.endswith(".xml"):
                continue
            try:
                content = open(p, encoding="utf-8", errors="ignore").read()
            except OSError:
                continue
            for m in re.finditer(r"@\+id/(\w+)", content):
                idx["id"].add(m.group(1))
            if folder.startswith("values"):
                try:
                    tree = ET.parse(p)
                except ET.ParseError as e:
                    err(f"XML {rel(p)}: {e}")
                    continue
                for node in tree.getroot():
                    name = node.get("name")
                    if not name:
                        continue
                    tag = node.tag
                    if tag == "string":
                        idx["string"].add(name)
                    elif tag == "style":
                        # В R.java точки в имени стиля заменяются на подчёркивания.
                        idx["style"].add(name.replace(".", "_"))
                    elif tag == "color":
                        idx["color"].add(name)
                    elif tag in ("string-array", "array", "integer-array"):
                        idx["array"].add(name)
                    elif tag == "dimen":
                        idx["dimen"].add(name)
                    elif tag == "bool":
                        idx["bool"].add(name)
                    elif tag == "integer":
                        idx["integer"].add(name)
                    elif tag == "attr":
                        idx["attr"].add(name)
    return idx


def check_r_refs(idx):
    rx = re.compile(r"\bR\.(id|layout|string|drawable|mipmap|style|color|xml|array|"
                    r"dimen|bool|integer|menu|anim|attr)\.(\w+)")
    n = 0
    for dp, _, fs in os.walk(P3_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            text = strip_comments(open(p, encoding="utf-8").read())
            for m in rx.finditer(text):
                # android.R.xxx и подобные — платформенные ресурсы, не наши.
                prefix = text[max(0, m.start() - 12):m.start()]
                if re.search(r"(androidx?|[a-z]+)\.$", prefix):
                    continue
                kind, name = m.group(1), m.group(2)
                n += 1
                if name not in idx.get(kind, set()):
                    err(f"RES {rel(p)}: R.{kind}.{name} отсутствует")
    print(f"[resources] {n} ссылок R.*")


# --- 4. XML ----------------------------------------------------------------
def check_xml():
    n = 0
    for dp, _, fs in os.walk(MAIN_ROOT):
        for f in fs:
            if not f.endswith(".xml"):
                continue
            p = os.path.join(dp, f)
            if "/assets/" in p.replace(os.sep, "/"):
                continue
            n += 1
            try:
                ET.parse(p)
            except ET.ParseError as e:
                err(f"XML {rel(p)}: {e}")
    print(f"[xml] {n} XML-файлов")


# --- 5. forbidden ----------------------------------------------------------
ALLOW_HARDCODE = {"com/p3win/P3Application.java"}


def check_forbidden():
    rx = re.compile(r'"/data/data/com\.winlator')
    for dp, _, fs in os.walk(P3_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            key = os.path.relpath(p, JAVA_ROOT).replace(os.sep, "/")
            text = open(p, encoding="utf-8").read()
            if rx.search(text) and key not in ALLOW_HARDCODE:
                err(f"HARDCODE {rel(p)}: жёсткий путь /data/data/com.winlator")
            if "if (false)" in text or "if(false)" in text:
                err(f"DEADCODE {rel(p)}: if (false)")
    print("[forbidden] hardcode / dead code")


# --- 5b. R-import correctness ---------------------------------------------
def check_r_imports():
    """R генерируется в namespace из app/build.gradle (сейчас com.winlator).
    Каждый файл, использующий R.*, обязан импортировать правильный R."""
    gradle = open(os.path.join(ROOT, "app/build.gradle"), encoding="utf-8").read()
    m = re.search(r"namespace\s+['\"]([\w.]+)['\"]", gradle)
    namespace = m.group(1) if m else "com.winlator"
    expected_import = f"import {namespace}.R;"

    n = 0
    for dp, _, fs in os.walk(P3_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            text = open(p, encoding="utf-8").read()
            code = strip_comments(text)
            uses_r = re.search(r"(?<![\w.])R\.(id|layout|string|drawable|style|color|menu|xml|dimen)\.",
                               code) is not None
            if not uses_r:
                continue
            n += 1
            if expected_import not in text and f"{namespace}.R." not in text:
                err(f"R {rel(p)}: ожидается «{expected_import}» (namespace={namespace})")
            for wrong in re.findall(r"import\s+(com\.[\w.]+\.R);", text):
                if wrong != f"{namespace}.R":
                    err(f"R {rel(p)}: неверный R-класс «{wrong}», namespace={namespace}")
    print(f"[R-import] {n} файлов, namespace={namespace}")


# --- 6. layering -----------------------------------------------------------
def check_layer():
    for dp, _, fs in os.walk(CORE_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            if re.search(r"^import\s+com\.p3win\.", open(p, encoding="utf-8").read(), re.M):
                err(f"LAYER {rel(p)}: ядро импортирует com.p3win")
    print("[layer] ядро не зависит от com.p3win")


# --- 7. core member existence ---------------------------------------------
def _core_index():
    idx = {}
    for dp, _, fs in os.walk(CORE_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            cls = f[:-5]
            text = open(p, encoding="utf-8").read()
            members = set(re.findall(
                r"(?:public|protected|private|static|final|synchronized|abstract|native|default|\s)+"
                r"[\w<>\[\], .?]+\s(\w+)\s*\(", text))
            members |= set(re.findall(
                r"(?:public|protected)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\], .?]+\s(\w+)\s*[;=]", text))
            members |= set(re.findall(r"\b(\w+)\s*\([^;{]*\)\s*\{", text))
            parent = None
            m = re.search(r"\b(?:class|interface|enum)\s+\w+(?:<[^>]*>)?\s+extends\s+([\w.]+)", text)
            if m:
                parent = m.group(1).split(".")[-1]
            if cls in idx:
                _, acc, par = idx[cls]
                idx[cls] = (p, acc | members, par or parent)
            else:
                idx[cls] = (p, members, parent)
    return idx


def _members(idx, cls, depth=0):
    if cls not in idx or depth > 6:
        return set()
    _, mem, parent = idx[cls]
    if parent and parent in idx:
        return mem | _members(idx, parent, depth + 1)
    return mem


def check_core_calls():
    try:
        import javalang
    except ImportError:
        warn("javalang не установлен — обращения к ядру не проверены")
        return
    idx = _core_index()
    n = 0
    for dp, _, fs in os.walk(P3_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            text = open(p, encoding="utf-8").read()
            try:
                tree = javalang.parse.parse(text)
            except Exception:
                continue

            imported = {}
            for imp in tree.imports:
                if imp.path.startswith("com.winlator."):
                    imported[imp.path.split(".")[-1]] = imp.path

            vars_ = {}
            for _, node in tree:
                decl = None
                if isinstance(node, (javalang.tree.LocalVariableDeclaration,
                                     javalang.tree.FieldDeclaration)):
                    decl = node
                if decl is None:
                    continue
                tnode = decl.type
                tname = None
                if isinstance(tnode, javalang.tree.ReferenceType):
                    tname = tnode.name
                elif isinstance(tnode, javalang.tree.BasicType):
                    tname = tnode.name
                if not tname:
                    continue
                tname = tname.split(".")[-1]
                core_name = None
                if tname in idx:
                    core_name = tname
                elif tname in imported and imported[tname].split(".")[-1] in idx:
                    core_name = imported[tname].split(".")[-1]
                if core_name:
                    for d in decl.declarators:
                        vars_[d.name] = core_name

            for _, node in tree:
                if not isinstance(node, javalang.tree.MethodInvocation):
                    continue
                q = node.qualifier
                if not q:
                    continue
                recv = q.split(".")[-1]
                cls = None
                if recv in vars_:
                    cls = vars_[recv]
                elif recv in imported and imported[recv].split(".")[-1] in idx:
                    cls = imported[recv].split(".")[-1]
                elif recv in idx and recv[0].isupper():
                    cls = recv
                if not cls:
                    continue
                n += 1
                if node.member not in _members(idx, cls):
                    line = node.position.line if node.position else "?"
                    err(f"CALL {rel(p)}:{line}: {cls} (через «{recv}») "
                        f"не имеет члена «{node.member}»")
    print(f"[calls] {n} обращений к ядру")


# --- 5c. Тема диалогов -----------------------------------------------------
def check_dialog_theme():
    """
    android.app.AlertDialog с темой Theme.AppCompat.* падает в рантайме:
    AppCompat-тема содержит атрибуты, которые системный диалог не понимает,
    и Android выбрасывает IllegalArgumentException при show().

    Ошибка невидима для компилятора (обе темы — просто @style/...), поэтому
    ловится здесь: если рядом с Builder стоит R.style.Theme_P3Win_Dialog,
    класс диалога обязан быть androidx.appcompat.app.AlertDialog.
    """
    bad = 0
    for dp, _, fs in os.walk(P3_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            text = strip_comments(open(p, encoding="utf-8").read())
            if "Theme_P3Win_Dialog" not in text:
                continue
            # Важно ловить не только полное имя, но и импорт: чаще всего код
            # пишет "import android.app.AlertDialog" и дальше AlertDialog.Builder(..),
            # и именно так выглядел реальный дефект.
            uses_framework = ("android.app.AlertDialog" in text
                              and re.search(r'\bAlertDialog\s*\.\s*Builder', text))
            if uses_framework:
                err(f"DIALOG {rel(p)}: android.app.AlertDialog с темой AppCompat — "
                    f"используйте androidx.appcompat.app.AlertDialog")
                bad += 1
    print(f"[dialog] тема диалогов проверена ({bad} проблем)")


# --- 5d. Ссылки на несуществующие скрипты/документы -----------------------
def check_doc_refs():
    """
    Комментарий, отправляющий читателя к несуществующему файлу, хуже
    отсутствия комментария: он создаёт уверенность, что инструмент есть.
    """
    rx = re.compile(r'(?:scripts|docs)/[A-Za-z0-9_./-]+\.(?:ps1|sh|py|md)')
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    missing = set()
    for dp, _, fs in os.walk(P3_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            text = open(os.path.join(dp, f), encoding="utf-8").read()
            for match in rx.findall(text):
                if not os.path.exists(os.path.join(repo, match)):
                    missing.add(match)
    for m in sorted(missing):
        warn(f"DOCREF: упоминается отсутствующий файл {m}")
    print(f"[docs] ссылки на скрипты проверены ({len(missing)} отсутствуют)")


def main():
    check_syntax()
    check_imports()
    check_r_refs(index_resources())
    check_xml()
    check_forbidden()
    check_r_imports()
    check_layer()
    check_core_calls()
    check_dialog_theme()
    check_doc_refs()
    print()
    if warnings:
        print(f"ПРЕДУПРЕЖДЕНИЯ ({len(warnings)}):")
        for w in warnings[:40]:
            print("  ~", w)
    if errors:
        print(f"ОШИБКИ ({len(errors)}):")
        for e in errors[:200]:
            print("  x", e)
        return 1
    print("OK: статические проверки пройдены")
    return 0


if __name__ == "__main__":
    sys.exit(main())
