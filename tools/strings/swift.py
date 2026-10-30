"""Small, strict Swift text parser. iOS: AppStrings*.swift, Translations+*.swift."""

from dataclasses import dataclass
import re


STRING_START = re.compile(r'(#+)?("""|")')
CODE_TOKEN = re.compile(r'[A-Za-z_$][A-Za-z_0-9$]*|[0-9]+|->')


class Unsupported(ValueError):
    pass


@dataclass(frozen=True)
class Token:
    value: str
    line: int
    kind: str = "code"
    interpolated: bool = False


def _literal(source, start):
    match = STRING_START.match(source, start)
    hashes, quote = match.group(1) or "", match.group(2)
    pos = start + len(match.group())
    end_marker = quote + hashes
    escape = "\\" + hashes
    result = []
    interpolated = False
    while pos < len(source):
        if source.startswith(end_marker, pos):
            value = "".join(result)
            if quote == '"""':
                if not value.startswith("\n"):
                    raise Unsupported("multiline literal must start on the next line")
                value = value[1:]
                indent = value.rsplit("\n", 1)[-1]
                if indent.strip():
                    raise Unsupported("multiline closing delimiter must have its own line")
                value = value[:len(value) - len(indent)] if indent else value
                value = value[:-1] if value.endswith("\n") else value
                lines = value.split("\n")
                if any(line.strip() and not line.startswith(indent) for line in lines):
                    raise Unsupported("invalid multiline indentation")
                value = "\n".join(line[len(indent):] if line.startswith(indent) else "" for line in lines)
            if any(0xD800 <= ord(char) <= 0xDFFF for char in value):
                value = value.encode("utf-16-le", "surrogatepass").decode("utf-16-le")
            return value, pos + len(end_marker), interpolated
        if source.startswith(escape, pos):
            pos += len(escape)
            char = source[pos]
            if char == "(":
                interpolated = True
                begin = pos
                depth = 1
                pos += 1
                while depth and pos < len(source):
                    if STRING_START.match(source, pos):
                        _, pos, _ = _literal(source, pos)
                        continue
                    if source[pos] == "(":
                        depth += 1
                    if source[pos] == ")":
                        depth -= 1
                    pos += 1
                if depth:
                    raise Unsupported("unclosed interpolation")
                result.append(escape + source[begin:pos])
                continue
            if char == "u" and source[pos + 1:pos + 2] == "{":
                end = source.index("}", pos + 2)
                result.append(chr(int(source[pos + 2:end], 16)))
                pos = end + 1
                continue
            # InfoPlist.strings also permits the Foundation \Uhhhh spelling.
            if char == "U":
                result.append(chr(int(source[pos + 1:pos + 5], 16)))
                pos += 5
                continue
            escapes = {"n": "\n", "r": "\r", "t": "\t", "0": "\0", '"': '"', "'": "'", "\\": "\\"}
            if char in escapes:
                result.append(escapes[char])
                pos += 1
                continue
            if char == "\n" and quote == '"""':
                # Remove the continuation together with indentation on its next line.
                pos += 1
                while pos < len(source) and source[pos] in " \t":
                    pos += 1
                continue
            raise Unsupported(f"unknown Swift escape {escape + char!r}")
        result.append(source[pos])
        pos += 1
    raise Unsupported("unclosed string literal")


def lex(source):
    tokens = []
    pos, line = 0, 1
    while pos < len(source):
        begin = pos
        if source[pos].isspace():
            pos += 1
        elif source.startswith("//", pos):
            end = source.find("\n", pos)
            pos = len(source) if end < 0 else end
        elif source.startswith("/*", pos):
            depth = 1
            pos += 2
            while depth and pos < len(source):
                if source.startswith("/*", pos):
                    depth += 1
                    pos += 2
                elif source.startswith("*/", pos):
                    depth -= 1
                    pos += 2
                else:
                    pos += 1
            if depth:
                raise Unsupported(f"unclosed comment at line {line}")
        elif STRING_START.match(source, pos):
            value, pos, interpolation = _literal(source, pos)
            tokens.append(Token(value, line, "string", interpolation))
        else:
            match = CODE_TOKEN.match(source, pos)
            value = match.group() if match else source[pos]
            tokens.append(Token(value, line))
            pos += len(value)
        line += source[begin:pos].count("\n")
    return tokens


def matching(tokens, start):
    pairs = {"(": ")", "[": "]", "{": "}"}
    stack = []
    for index in range(start, len(tokens)):
        token = tokens[index]
        if token.kind != "code":
            continue
        if token.value in pairs:
            stack.append(pairs[token.value])
        elif token.value in pairs.values():
            if not stack or token.value != stack.pop():
                raise Unsupported(f"unbalanced delimiter at line {token.line}")
            if not stack:
                return index
    raise Unsupported(f"unclosed delimiter at line {tokens[start].line}")


def split(tokens, delimiter=","):
    parts, begin, index = [], 0, 0
    while index < len(tokens):
        if tokens[index].kind == "code" and tokens[index].value in ("(", "[", "{"):
            index = matching(tokens, index) + 1
            continue
        if tokens[index].value == delimiter and tokens[index].kind == "code":
            parts.append(tokens[begin:index])
            begin = index + 1
        index += 1
    if begin < len(tokens):
        parts.append(tokens[begin:])
    return parts


def arguments(tokens):
    positional, named = [], {}
    for part in split(tokens):
        if len(part) >= 2 and part[1].value == ":" and part[0].kind == "code":
            if part[0].value in named:
                raise Unsupported("duplicate argument")
            named[part[0].value] = part[2:]
        else:
            positional.append(part)
    return positional, named


def literal(tokens):
    if tokens and tokens[0].value == "(" and tokens[0].kind == "code" and matching(tokens, 0) == len(tokens) - 1:
        return literal(tokens[1:-1])
    parts = split(tokens, "+")
    if len(parts) > 1:
        return "".join(literal(part) for part in parts)
    if len(tokens) != 1 or tokens[0].kind != "string":
        raise Unsupported("expression is not a constant string")
    if tokens[0].interpolated:
        raise Unsupported("Swift interpolation requires a manual port")
    return tokens[0].value


@dataclass
class Function:
    name: str
    line: int
    signature: list
    body: list


def bindings(tokens):
    for index in range(len(tokens) - 2):
        if tokens[index].value != "static" or tokens[index + 1].value not in ("let", "var"):
            continue
        name, line = tokens[index + 2].value, tokens[index].line
        begin = index + 3
        while begin < len(tokens) and tokens[begin].value not in ("=", "{", "}", "static"):
            begin += 1
        if begin == len(tokens) or tokens[begin].value != "=":
            yield name, line, []
            continue
        begin += 1
        end = begin
        while end < len(tokens):
            token = tokens[end]
            if token.kind == "code" and token.value in ("}", ";", "static"):
                break
            if end > begin and token.line > tokens[end - 1].line and token.value != "+" and tokens[end - 1].value != "+":
                break
            if token.kind == "code" and token.value in ("(", "[", "{"):
                end = matching(tokens, end) + 1
            else:
                end += 1
        yield name, line, tokens[begin:end]


def functions(tokens):
    for index, token in enumerate(tokens):
        if token.kind != "code" or token.value != "func":
            continue
        name = tokens[index + 1].value
        opening = index + 2
        while opening < len(tokens) and tokens[opening].value != "(":
            opening += 1
        closing = matching(tokens, opening)
        brace = closing + 1
        while brace < len(tokens) and tokens[brace].value != "{":
            brace += 1
        end = matching(tokens, brace)
        # Include helpers as well: their logic is part of the hand-port inventory.
        yield Function(name, token.line, tokens[opening + 1:brace], tokens[brace + 1:end])


def calls(tokens, name):
    for index in range(len(tokens) - 1):
        if tokens[index].kind == "code" and tokens[index].value == name and tokens[index + 1].value == "(":
            end = matching(tokens, index + 1)
            yield index, end, arguments(tokens[index + 2:end])


def expression_body(tokens):
    return tokens[1:] if tokens and tokens[0].value == "return" else tokens


def language_cases(tokens, languages):
    """Accept an entire switch on the language; reject guards and extra statements."""
    if len(tokens) < 4 or tokens[0].value != "switch" or tokens[2].value != "{":
        raise Unsupported("not a plain language switch")
    if matching(tokens, 2) != len(tokens) - 1:
        raise Unsupported("statements around language switch")
    body = tokens[3:-1]
    cases, index = {}, 0
    while index < len(body):
        if body[index].value not in ("case", "default"):
            raise Unsupported("unsupported switch case")
        default = body[index].value == "default"
        colon = index + 1
        while colon < len(body) and body[colon].value != ":":
            colon += 1
        labels = [literal(part) if len(part) == 1 and part[0].kind == "string" else "".join(t.value for t in part).lstrip(".") for part in split(body[index + 1:colon])]
        end = colon + 1
        while end < len(body) and not (body[end].kind == "code" and body[end].value in ("case", "default")):
            if body[end].kind == "code" and body[end].value in ("(", "[", "{"):
                end = matching(body, end) + 1
            else:
                end += 1
        for lang in languages if default else labels:
            if lang not in languages:
                raise Unsupported("switch is not over supported languages")
            if not default or lang not in cases:
                cases[lang] = expression_body(body[colon + 1:end])
        index = end
    if set(cases) != set(languages):
        raise Unsupported("language switch is incomplete")
    return cases
