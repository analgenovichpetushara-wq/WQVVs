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
import collections
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


# --- 5e. Арность вызовов внутри P3 ----------------------------------------
def check_p3_arity():
    """
    Проверяет число аргументов у вызовов методов собственных классов P3.

    Зачем: переименование сигнатуры (например, onSessionActivityCreated
    получил четвёртый параметр) ломает сборку, но старый анализатор этого не
    видел — проверялись только члены классов ядра. Здесь строится индекс
    «класс → метод → допустимая арность» по всему com/p3win и сверяются
    вызовы через переменные и статические обращения.

    Ограничения осознанные: наследование не разворачивается, перегрузки
    считаются по множеству допустимых арностей, вызовы super/this не
    проверяются. Цель — грубая сетка, а не компилятор.
    """
    try:
        import javalang
    except ImportError:
        warn("javalang не установлен — арность вызовов P3 не проверена")
        return

    types = collections.defaultdict(lambda: collections.defaultdict(set))
    parsed = []
    for dp, _, fs in os.walk(P3_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            try:
                tree = javalang.parse.parse(open(p, encoding="utf-8").read())
            except Exception:
                continue
            parsed.append((p, tree))
            for _, node in tree:
                if not isinstance(node, javalang.tree.ClassDeclaration):
                    continue
                for _, m in node:
                    if isinstance(m, javalang.tree.MethodDeclaration):
                        params = m.parameters or []
                        types[node.name][m.name].add(
                            (len(params), any(getattr(x, "varargs", False) for x in params)))

    skip = {
        "onCreate", "onDestroy", "onResume", "onPause", "onStart", "onStop",
        "onSaveInstanceState", "onCreateView", "onViewCreated", "onActivityCreated",
        "onDestroyView", "onAttach", "onDetach", "onRequestPermissionsResult",
        "onActivityResult", "onBackPressed", "onConfigurationChanged", "onClick",
    }

    n = 0
    for p, tree in parsed:
        vars_ = {}
        for _, node in tree:
            if not isinstance(node, (javalang.tree.LocalVariableDeclaration,
                                     javalang.tree.FieldDeclaration)):
                continue
            tnode = node.type
            tname = None
            if isinstance(tnode, javalang.tree.ReferenceType):
                tname = tnode.name.split(".")[-1]
            if tname in types:
                for d in node.declarators:
                    vars_[d.name] = tname

        for _, node in tree:
            if not isinstance(node, javalang.tree.MethodInvocation):
                continue
            q = node.qualifier
            cls = None
            if q and q.split(".")[-1] in vars_:
                cls = vars_[q.split(".")[-1]]
            elif q and q.split(".")[-1] in types:
                cls = q.split(".")[-1]
            if not cls or node.member not in types[cls] or node.member in skip:
                continue
            arity = len(node.arguments or [])
            allowed = types[cls][node.member]
            n += 1
            if not any(a == arity or (v and arity >= a) for a, v in allowed):
                line = node.position.line if node.position else "?"
                err(f"ARITY {rel(p)}:{line}: {cls}.{node.member} вызван с {arity} арг., "
                    f"объявлено: {sorted(a for a, _ in allowed)}")
    print(f"[arity] {n} вызовов методов P3")


# --- 5f. Целостность ресурсов ---------------------------------------------
def _resource_index():
    """
    Индекс всех ресурсов проекта.

    Тонкость, на которой легко ошибиться: картинка может лежать в любой
    density-папке (drawable-hdpi, drawable-xxhdpi) и носить суффикс .9.png.
    Поэтому имя ресурса — это имя файла без расширения (и без «.9»), а не
    stem из одной папки drawable.
    """
    res = os.path.join(MAIN_ROOT, "res")
    index = collections.defaultdict(set)
    if not os.path.isdir(res):
        return index

    for folder in os.listdir(res):
        path = os.path.join(res, folder)
        if not os.path.isdir(path):
            continue
        if folder == "values" or folder.startswith("values-"):
            continue
        for entry in os.listdir(path):
            full = os.path.join(path, entry)
            if not os.path.isfile(full):
                continue
            name = entry
            for suffix in (".9.png", ".png", ".xml", ".webp", ".jpg", ".jpeg", ".gif"):
                if name.endswith(suffix):
                    name = name[: -len(suffix)]
                    break
            index[folder.split("-")[0]].add(name)

    for folder in os.listdir(res):
        path = os.path.join(res, folder)
        if not (folder == "values" or folder.startswith("values-")):
            continue
        if not os.path.isdir(path):
            continue
        for entry in os.listdir(path):
            if not entry.endswith(".xml"):
                continue
            try:
                tree = ET.parse(os.path.join(path, entry))
            except ET.ParseError:
                continue
            for el in tree.getroot():
                name = el.attrib.get("name")
                if name:
                    index[el.tag].add(name)
    return index


def check_resource_refs():
    """
    Каждая ссылка @type/name в разметке P3 должна существовать.

    AAPT падает на отсутствующем ресурсе, но сообщение указывает на
    сгенерированный файл, а не на опечатку, — искать приходится руками.
    Проверяются только p3_*-файлы: ядро трогать не наша задача.
    """
    index = _resource_index()
    by_file = {}
    for dp, _, fs in os.walk(os.path.join(MAIN_ROOT, "res", "layout")):
        for f in fs:
            if f.startswith("p3_") and f.endswith(".xml"):
                by_file[os.path.join(dp, f)] = None

    # drawable, объявленные внутри state-list как item'ы, тоже валидны
    for entry in os.listdir(os.path.join(MAIN_ROOT, "res", "drawable")):
        if not entry.endswith(".xml"):
            continue
        try:
            tree = ET.parse(os.path.join(MAIN_ROOT, "res", "drawable", entry))
        except ET.ParseError:
            continue
        for el in tree.getroot():
            value = el.attrib.get("{http://schemas.android.com/apk/res/android}drawable", "")
            if value.startswith("@drawable/"):
                index["drawable"].add(value[len("@drawable/"):])

    ref_rx = re.compile(r"@(?!\+?id/|android:)([a-z]+)/([\w.]+)")
    n = 0
    for path in by_file:
        text = open(path, encoding="utf-8").read()
        for kind, name in ref_rx.findall(text):
            if kind not in index:
                continue
            n += 1
            if name in index[kind]:
                continue
            err(f"RES {rel(path)}: @{kind}/{name} не существует")
    print(f"[res] {n} ссылок в разметке P3 проверено")


def check_unused_p3_drawables():
    """
    Неиспользуемые p3_*-ресурсы: не ошибка, но мусор в APK и повод
    задуматься, не забыта ли кнопка. Выводится предупреждением.
    """
    index = _resource_index()
    used = set()
    for dp, _, fs in os.walk(MAIN_ROOT):
        for f in fs:
            if not f.endswith((".xml", ".java")):
                continue
            text = open(os.path.join(dp, f), encoding="utf-8", errors="ignore").read()
            used.update(re.findall(r"@drawable/([\w.]+)", text))
            used.update(re.findall(r"R\.drawable\.(\w+)", text))

    # Часть иконок зарезервирована дизайн-системой под будущие экраны
    # (меню с иконками, seekbar настроек). Это не мусор, а согласованный
    # набор: удалять их — значит каждый раз заново рисовать.
    # Набор иконок дизайн-системы (docs/01_PHASE_MAP.md §2). Он полный по
    # замыслу: экран, которому иконка понадобится завтра, не должен ждать
    # художника. Проверка оставлена, чтобы список не расползался случайно.
    reserved = {
        "p3_card_background_accent", "p3_card_background_high", "p3_card_ripple",
        "p3_divider", "p3_seekbar_progress", "p3_seekbar_thumb",
        "p3_ic_close", "p3_ic_duplicate", "p3_ic_export", "p3_ic_memory",
        "p3_ic_speed", "p3_ic_star", "p3_ic_stop", "p3_ic_trash",
    }
    unused = sorted(x for x in index["drawable"]
                    if x.startswith("p3_") and x not in used and x not in reserved)
    for name in unused:
        warn(f"UNUSED: drawable/{name} не используется")
    print(f"[res] неиспользуемых p3 drawable: {len(unused)}")


# --- 5g. Неиспользуемые импорты -------------------------------------------
def check_unused_imports():
    """
    Импорт без использования — не ошибка компиляции, но он вводит в
    заблуждение при чтении и мешает заметить настоящую зависимость файла.
    Проверяются только импорты com.p3win.* и com.winlator.*: импорты из
    Android SDK часто «используются» только в javadoc.
    """
    total = 0
    for dp, _, fs in os.walk(P3_ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            raw = open(p, encoding="utf-8").read()
            code = strip_comments(raw)
            for match in re.finditer(r"^import\s+((?:com\.p3win|com\.winlator)\.[\w.]+);", raw, re.M):
                fqcn = match.group(1)
                simple = fqcn.split(".")[-1]
                # имя используется, если встречается вне строки импорта
                body = code.replace(match.group(0), "")
                if re.search(r"\b" + re.escape(simple) + r"\b", body):
                    continue
                total += 1
                warn(f"IMPORT {rel(p)}: не используется {fqcn}")
    print(f"[imports] неиспользуемых P3/ядро импортов: {total}")


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
    check_p3_arity()
    check_resource_refs()
    check_unused_p3_drawables()
    check_unused_imports()
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
