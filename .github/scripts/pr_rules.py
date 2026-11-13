"""PR 규칙 검사 — CONTRIBUTING.md 의 기계적으로 판정할 수 있는 부분만.

squash merge 라 PR 제목이 곧 main 의 커밋 제목이 된다. 그래서 제목 형식을 본다.
공개 저장소라 커밋에 실제 이메일이 남으면 안 된다. 그래서 작성자·커미터 이메일을 본다.

    python3 pr_rules.py title "<PR 제목>"
    git log --format='%ae%n%ce' base..head | python3 pr_rules.py emails
"""
import re
import sys

TYPES = ("feat", "fix", "refactor", "test", "docs", "build", "ci", "chore")
TITLE = re.compile(r"^(?P<type>[a-z]+)(?:\((?P<scope>[^)]*)\))?: (?P<summary>.*)$")
SCOPE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
MAX_SUMMARY = 50

# GitHub 웹에서 merge·커밋하면 커미터가 이 주소로 찍힌다
GITHUB_WEB = "noreply@github.com"
NOREPLY = re.compile(r"^[^@\s]+@users\.noreply\.github\.com$")


def title_problem(title: str) -> str | None:
    """형식에 맞으면 None, 아니면 이유."""
    m = TITLE.match(title)
    if not m:
        return "`type(scope): 요약` 형식이 아니다 (콜론 뒤에 공백 하나)"
    if m["type"] not in TYPES:
        return f"type `{m['type']}` 은 쓸 수 없다. 허용: {', '.join(TYPES)}"
    if m["scope"] is not None and not SCOPE.match(m["scope"]):
        return f"scope `{m['scope']}` 는 영소문자·숫자·하이픈만 쓴다"
    summary = m["summary"].strip()
    if not summary:
        return "요약이 비었다"
    if summary.endswith("."):
        return "요약 끝에 마침표를 찍지 않는다"
    if len(summary) > MAX_SUMMARY:
        return f"요약이 {len(summary)}자다. {MAX_SUMMARY}자 안쪽으로 줄인다"
    return None


def bad_emails(emails: list[str]) -> list[str]:
    """noreply 가 아닌 이메일을 처음 나온 순서대로 한 번씩."""
    out: list[str] = []
    for e in (x.strip() for x in emails):
        if e and e != GITHUB_WEB and not NOREPLY.match(e) and e not in out:
            out.append(e)
    return out


def main(argv: list[str]) -> int:
    if len(argv) == 3 and argv[1] == "title":
        problem = title_problem(argv[2])
        if problem:
            print(f"::error title=PR 제목::{problem} — 받은 제목: {argv[2]}")
            return 1
        print("PR 제목 형식 통과")
        return 0
    if len(argv) == 2 and argv[1] == "emails":
        bad = bad_emails(sys.stdin.read().splitlines())
        if bad:
            for e in bad:
                print(f"::error title=커밋 이메일::{e} 는 GitHub noreply 주소가 아니다 (CONTRIBUTING.md)")
            return 1
        print("커밋 이메일 통과")
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
