import unittest

from pr_rules import bad_emails, title_problem


class TitleTest(unittest.TestCase):
    def test_형식에_맞는_제목은_통과(self):
        for title in [
            "feat(parking): P단 진입 시 주차 세션 시작",
            "fix: 무료 시간 0분 표기",
            "ci(pr): 제목 형식 검사",
            "build: app·core 모듈 뼈대와 CI",
        ]:
            self.assertIsNone(title_problem(title), title)

    def test_type_이_없거나_모르는_것이면_실패(self):
        self.assertIsNotNone(title_problem("수정"))
        self.assertIsNotNone(title_problem("update readme"))
        self.assertIsNotNone(title_problem("feature: 무언가"))

    def test_scope_는_영소문자와_하이픈만(self):
        self.assertIsNotNone(title_problem("feat(Parking): 세션"))
        self.assertIsNotNone(title_problem("feat(주차): 세션"))
        self.assertIsNone(title_problem("feat(gear-poll): 세션"))

    def test_콜론_뒤_공백과_요약이_있어야_한다(self):
        self.assertIsNotNone(title_problem("feat:세션"))
        self.assertIsNotNone(title_problem("feat: "))

    def test_마침표로_끝나면_실패(self):
        self.assertIsNotNone(title_problem("fix: 버그 고침."))

    def test_요약은_50자까지(self):
        self.assertIsNone(title_problem("docs: " + "가" * 50))
        self.assertIsNotNone(title_problem("docs: " + "가" * 51))


class EmailTest(unittest.TestCase):
    def test_noreply_주소는_통과(self):
        self.assertEqual(bad_emails(["73851304+daeuk1011@users.noreply.github.com"]), [])

    def test_GitHub_웹_머지_커미터는_통과(self):
        self.assertEqual(bad_emails(["noreply@github.com"]), [])

    def test_실제_이메일은_걸러낸다(self):
        self.assertEqual(
            bad_emails(["someone@gmail.com", "1+a@users.noreply.github.com", "x@company.com"]),
            ["someone@gmail.com", "x@company.com"],
        )

    def test_중복은_한_번만(self):
        self.assertEqual(bad_emails(["a@b.com", "a@b.com"]), ["a@b.com"])


if __name__ == "__main__":
    unittest.main()
