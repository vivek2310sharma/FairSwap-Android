#!/usr/bin/env python3
import os
import sys
import subprocess
import hashlib
import json
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from datetime import datetime, timezone

REQUIRED_REPORTS = [
    ("feature/communication/build/test-results/testDebugUnitTest", "com.fairswap.feature.communication.NotificationsViewModelTest"),
    ("app/build/test-results/testStagingDebugUnitTest", "com.fairswap.app.ui.FairSwapAppTest"),
]

def parse_and_validate_reports(reports, required_reports=None):
    if not reports:
        return {
            "success": False,
            "error": "No XML test reports found.",
            "total_tests": 0,
            "unique_tests": 0,
            "total_failures": 0,
            "total_errors": 0,
            "total_skipped": 0,
            "suites_found": [],
            "unique_test_names": set(),
            "failures_detail": []
        }

    if required_reports is None:
        required_reports = REQUIRED_REPORTS

    suites_found = set()
    unique_test_names = set()
    failures_detail = []
    total_tests = 0
    total_failures = 0
    total_errors = 0
    total_skipped = 0

    # To check required module+variant+suite tuple
    found_required = set()

    for rep in reports:
        rep_path = Path(rep)
        rep_path_str = str(rep_path).replace("\\", "/")

        try:
            tree = ET.parse(rep)
            root = tree.getroot()
        except Exception as ex:
            return {
                "success": False,
                "error": f"Malformed XML report {rep}: {ex}",
                "total_tests": total_tests,
                "unique_tests": len(unique_test_names),
                "total_failures": total_failures,
                "total_errors": total_errors,
                "total_skipped": total_skipped,
                "suites_found": list(suites_found),
                "unique_test_names": unique_test_names,
                "failures_detail": failures_detail
            }

        suites = [root] if root.tag == "testsuite" else root.findall(".//testsuite")
        for suite in suites:
            s_name = suite.get("name", "")
            if s_name:
                suites_found.add(s_name)

            for req_path, req_suite in required_reports:
                if s_name == req_suite and req_path in rep_path_str:
                    found_required.add((req_path, req_suite))

            t = int(suite.get("tests", "-1"))
            f = int(suite.get("failures", "-1"))
            e = int(suite.get("errors", "-1"))
            s = int(suite.get("skipped", "-1"))

            if t == -1 or f == -1 or e == -1 or s == -1:
                return {
                    "success": False,
                    "error": f"Omitted counters in suite {s_name} in report {rep}",
                    "total_tests": total_tests,
                    "unique_tests": len(unique_test_names),
                    "total_failures": total_failures,
                    "total_errors": total_errors,
                    "total_skipped": total_skipped,
                    "suites_found": list(suites_found),
                    "unique_test_names": unique_test_names,
                    "failures_detail": failures_detail
                }

            total_tests += t
            total_failures += f
            total_errors += e
            total_skipped += s

            actual_cases = 0
            actual_failures = 0
            actual_errors = 0
            actual_skipped = 0

            for case in suite.findall("testcase"):
                actual_cases += 1
                c_name = case.get("classname", "") + "::" + case.get("name", "")
                unique_test_names.add(c_name)

                fail_elem = case.find("failure")
                err_elem = case.find("error")
                skip_elem = case.find("skipped")

                if fail_elem is not None:
                    actual_failures += 1
                if err_elem is not None:
                    actual_errors += 1
                if skip_elem is not None:
                    actual_skipped += 1

                if fail_elem is not None or err_elem is not None or skip_elem is not None:
                    outcome = "FAILURE" if fail_elem is not None else ("ERROR" if err_elem is not None else "SKIPPED")
                    failures_detail.append(f"{c_name} [{outcome}] in {rep}")

            if actual_failures > 0 and f == 0:
                return {"success": False, "error": f"failure element present but failures=\"0\" in {rep}", "total_tests": total_tests, "unique_tests": len(unique_test_names), "total_failures": total_failures, "total_errors": total_errors, "total_skipped": total_skipped, "suites_found": list(suites_found), "unique_test_names": unique_test_names, "failures_detail": failures_detail}
            if actual_errors > 0 and e == 0:
                return {"success": False, "error": f"error element present but errors=\"0\" in {rep}", "total_tests": total_tests, "unique_tests": len(unique_test_names), "total_failures": total_failures, "total_errors": total_errors, "total_skipped": total_skipped, "suites_found": list(suites_found), "unique_test_names": unique_test_names, "failures_detail": failures_detail}
            if actual_skipped > 0 and s == 0:
                return {"success": False, "error": f"skipped element present but skipped=\"0\" in {rep}", "total_tests": total_tests, "unique_tests": len(unique_test_names), "total_failures": total_failures, "total_errors": total_errors, "total_skipped": total_skipped, "suites_found": list(suites_found), "unique_test_names": unique_test_names, "failures_detail": failures_detail}
            if actual_cases != t:
                return {"success": False, "error": f"Declared tests count ({t}) inconsistent with actual cases ({actual_cases}) in {rep}", "total_tests": total_tests, "unique_tests": len(unique_test_names), "total_failures": total_failures, "total_errors": total_errors, "total_skipped": total_skipped, "suites_found": list(suites_found), "unique_test_names": unique_test_names, "failures_detail": failures_detail}

            for req_path, req_suite in required_reports:
                if s_name == req_suite and req_path in rep_path_str and actual_cases == 0:
                    return {"success": False, "error": f"Mandatory suite {s_name} contains zero cases in {rep}", "total_tests": total_tests, "unique_tests": len(unique_test_names), "total_failures": total_failures, "total_errors": total_errors, "total_skipped": total_skipped, "suites_found": list(suites_found), "unique_test_names": unique_test_names, "failures_detail": failures_detail}

    missing_reports = [req for req in required_reports if req not in found_required]
    if missing_reports:
        return {
            "success": False,
            "error": f"Missing required test suites: {missing_reports}",
            "total_tests": total_tests,
            "unique_tests": len(unique_test_names),
            "total_failures": total_failures,
            "total_errors": total_errors,
            "total_skipped": total_skipped,
            "suites_found": list(suites_found),
            "unique_test_names": unique_test_names,
            "failures_detail": failures_detail
        }

    if total_failures > 0 or total_errors > 0 or total_skipped > 0:
        return {
            "success": False,
            "error": f"Test failures/errors/skips detected: Failures={total_failures}, Errors={total_errors}, Skipped={total_skipped}. Details: {failures_detail}",
            "total_tests": total_tests,
            "unique_tests": len(unique_test_names),
            "total_failures": total_failures,
            "total_errors": total_errors,
            "total_skipped": total_skipped,
            "suites_found": list(suites_found),
            "unique_test_names": unique_test_names,
            "failures_detail": failures_detail
        }

    return {
        "success": True,
        "error": None,
        "total_tests": total_tests,
        "unique_tests": len(unique_test_names),
        "total_failures": 0,
        "total_errors": 0,
        "total_skipped": 0,
        "suites_found": list(suites_found),
        "unique_test_names": unique_test_names,
        "failures_detail": []
    }

def run_self_tests():
    print("--- Running Verifier Synthetic Self-Tests ---")
    results = {}

    with tempfile.TemporaryDirectory(prefix="verifier_selftest_") as tmp:
        tmp_path = Path(tmp)

        # Case 1: no_reports
        res = parse_and_validate_reports([], REQUIRED_REPORTS)
        results["no_reports"] = {"passed": not res["success"], "error": res["error"]}

        # Case 2: missing_required_suite
        unrelated_xml = tmp_path / "TEST-com.example.UnrelatedTest.xml"
        unrelated_xml.write_text('<testsuite name="com.example.UnrelatedTest" tests="1" failures="0" errors="0" skipped="0"><testcase name="test1" classname="com.example.UnrelatedTest"/></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([unrelated_xml], REQUIRED_REPORTS)
        results["missing_required_suite"] = {"passed": not res["success"], "error": res["error"]}

        # Case 3: malformed_report
        malformed_xml = tmp_path / "TEST-malformed.xml"
        malformed_xml.write_text('<testsuite name="com.fairswap.feature.communication.NotificationsViewModelTest"><unclosed_tag></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([malformed_xml], REQUIRED_REPORTS)
        results["malformed_report"] = {"passed": not res["success"], "error": res["error"]}

        # Case 4: failed_testcase
        failing_xml = tmp_path / "feature/communication/build/test-results/testDebugUnitTest/TEST-com.fairswap.feature.communication.NotificationsViewModelTest.xml"
        failing_xml.parent.mkdir(parents=True, exist_ok=True)
        failing_xml.write_text('<testsuite name="com.fairswap.feature.communication.NotificationsViewModelTest" tests="1" failures="1" errors="0" skipped="0"><testcase name="test1" classname="com.fairswap.feature.communication.NotificationsViewModelTest"><failure message="Failed"/></testcase></testsuite>', encoding="utf-8")
        req2_xml = tmp_path / "app/build/test-results/testStagingDebugUnitTest/TEST-com.fairswap.app.ui.FairSwapAppTest.xml"
        req2_xml.parent.mkdir(parents=True, exist_ok=True)
        req2_xml.write_text('<testsuite name="com.fairswap.app.ui.FairSwapAppTest" tests="1" failures="0" errors="0" skipped="0"><testcase name="test2" classname="com.fairswap.app.ui.FairSwapAppTest"/></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([failing_xml, req2_xml], REQUIRED_REPORTS)
        results["failed_testcase"] = {"passed": not res["success"], "error": res["error"]}

        # Case 5: skipped_test_reporting
        skipped_xml = tmp_path / "feature/communication/build/test-results/testDebugUnitTest/TEST-com.fairswap.feature.communication.NotificationsViewModelTest_skip.xml"
        skipped_xml.write_text('<testsuite name="com.fairswap.feature.communication.NotificationsViewModelTest" tests="1" failures="0" errors="0" skipped="1"><testcase name="test1" classname="com.fairswap.feature.communication.NotificationsViewModelTest"><skipped/></testcase></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([skipped_xml, req2_xml], REQUIRED_REPORTS)
        results["skipped_test_reporting"] = {"passed": not res["success"], "error": res["error"]}

        # Case 6: valid_required_reports
        valid_notif_xml = tmp_path / "feature/communication/build/test-results/testDebugUnitTest/TEST-valid_notif.xml"
        valid_notif_xml.write_text('<testsuite name="com.fairswap.feature.communication.NotificationsViewModelTest" tests="1" failures="0" errors="0" skipped="0"><testcase name="test1" classname="com.fairswap.feature.communication.NotificationsViewModelTest"/></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([valid_notif_xml, req2_xml], REQUIRED_REPORTS)
        results["valid_required_reports"] = {"passed": res["success"], "error": res["error"]}

        # Case 7: failure element with failures="0" -> reject
        fail_zero_xml = tmp_path / "feature/communication/build/test-results/testDebugUnitTest/TEST-fail_zero.xml"
        fail_zero_xml.write_text('<testsuite name="com.fairswap.feature.communication.NotificationsViewModelTest" tests="1" failures="0" errors="0" skipped="0"><testcase name="test1" classname="com.fairswap.feature.communication.NotificationsViewModelTest"><failure message="Failed"/></testcase></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([fail_zero_xml, req2_xml], REQUIRED_REPORTS)
        results["failure_element_failures_zero"] = {"passed": not res["success"], "error": res["error"]}

        # Case 8: error element with errors="0" -> reject
        error_zero_xml = tmp_path / "feature/communication/build/test-results/testDebugUnitTest/TEST-error_zero.xml"
        error_zero_xml.write_text('<testsuite name="com.fairswap.feature.communication.NotificationsViewModelTest" tests="1" failures="0" errors="0" skipped="0"><testcase name="test1" classname="com.fairswap.feature.communication.NotificationsViewModelTest"><error message="Error"/></testcase></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([error_zero_xml, req2_xml], REQUIRED_REPORTS)
        results["error_element_errors_zero"] = {"passed": not res["success"], "error": res["error"]}

        # Case 9: failure element with omitted counters -> reject
        omitted_counters_xml = tmp_path / "feature/communication/build/test-results/testDebugUnitTest/TEST-omitted.xml"
        omitted_counters_xml.write_text('<testsuite name="com.fairswap.feature.communication.NotificationsViewModelTest"><testcase name="test1" classname="com.fairswap.feature.communication.NotificationsViewModelTest"><failure message="Failed"/></testcase></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([omitted_counters_xml, req2_xml], REQUIRED_REPORTS)
        results["failure_omitted_counters"] = {"passed": not res["success"], "error": res["error"]}

        # Case 10: mandatory suite containing zero cases -> reject
        zero_cases_xml = tmp_path / "feature/communication/build/test-results/testDebugUnitTest/TEST-zero_cases.xml"
        zero_cases_xml.write_text('<testsuite name="com.fairswap.feature.communication.NotificationsViewModelTest" tests="0" failures="0" errors="0" skipped="0"></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([zero_cases_xml, req2_xml], REQUIRED_REPORTS)
        results["mandatory_suite_zero_cases"] = {"passed": not res["success"], "error": res["error"]}

        # Case 11: declared counts inconsistent with actual cases -> reject
        inconsistent_xml = tmp_path / "feature/communication/build/test-results/testDebugUnitTest/TEST-inconsistent.xml"
        inconsistent_xml.write_text('<testsuite name="com.fairswap.feature.communication.NotificationsViewModelTest" tests="2" failures="0" errors="0" skipped="0"><testcase name="test1" classname="com.fairswap.feature.communication.NotificationsViewModelTest"/></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([inconsistent_xml, req2_xml], REQUIRED_REPORTS)
        results["declared_counts_inconsistent"] = {"passed": not res["success"], "error": res["error"]}

        # Case 12: required staging app report missing, production report present -> reject
        prod_xml = tmp_path / "app/build/test-results/testProductionDebugUnitTest/TEST-prod.xml"
        prod_xml.parent.mkdir(parents=True, exist_ok=True)
        prod_xml.write_text('<testsuite name="com.fairswap.app.ui.FairSwapAppTest" tests="1" failures="0" errors="0" skipped="0"><testcase name="test1" classname="com.fairswap.app.ui.FairSwapAppTest"/></testsuite>', encoding="utf-8")
        res = parse_and_validate_reports([valid_notif_xml, prod_xml], REQUIRED_REPORTS)
        results["staging_missing_prod_present"] = {"passed": not res["success"], "error": res["error"]}

    all_passed = all(v["passed"] for v in results.values())
    for case_name, outcome in results.items():
        status_str = "PASS" if outcome["passed"] else "FAIL"
        print(f"Self-test case '{case_name}': {status_str} (error: {outcome['error']})")

    if not all_passed:
        print("ERROR: Self-tests failed!", file=sys.stderr)
        sys.exit(1)
    print("All synthetic verifier self-tests PASSED!\n")
    return results

def main():
    if "--self-test" in sys.argv:
        run_self_tests()
        sys.exit(0)

    # Automatically run self-tests first
    run_self_tests()

    root = Path.cwd().resolve()
    if not (root / "settings.gradle.kts").exists() and not (root / "settings.gradle").exists():
        print("ERROR: Must be run from repository root", file=sys.stderr)
        sys.exit(1)

    out_dir = Path(tempfile.mkdtemp(prefix="fairswap_verify_run_"))
    print(f"Run metadata directory: {out_dir}")

    env = os.environ.copy()
    env.pop("ANDROID_PREFS_ROOT", None)

    gates = [
        [":core:communication:test", "--stacktrace"],
        [":feature:communication:test", "--stacktrace"],
        [":feature:account:test", "--stacktrace"],
        [":feature:listingdetail:test", "--stacktrace"],
        [":app:testStagingDebugUnitTest", "--stacktrace"],
        [":feature:communication:lint", ":feature:account:lint", ":feature:listingdetail:lint", "--stacktrace"],
        [":app:assembleStagingDebug", "test", "lint", "--stacktrace"],
    ]

    gradle_wrapper = root / "gradlew"
    if not gradle_wrapper.exists():
        print("ERROR: gradlew not found", file=sys.stderr)
        sys.exit(1)

    gate_results = []
    for i, args in enumerate(gates, 1):
        cmd = [str(gradle_wrapper)] + args
        cmd_str = " ".join(args)
        print(f"\n--- Running Gate {i}/{len(gates)}: {cmd_str} ---")
        start_time = datetime.now(timezone.utc)

        try:
            res = subprocess.run(
                cmd,
                cwd=root,
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=600
            )
            duration = (datetime.now(timezone.utc) - start_time).total_seconds()
            log_file = out_dir / f"gate_{i}.log"
            log_file.write_text(res.stdout, encoding="utf-8")

            gate_results.append({
                "gate": i,
                "command": cmd_str,
                "exit_code": res.returncode,
                "duration_seconds": duration,
                "log_file": str(log_file)
            })

            if res.returncode != 0:
                print(f"ERROR: Gate {i} failed with exit code {res.returncode}", file=sys.stderr)
                print(res.stdout[-2000:])
                sys.exit(res.returncode)

            print(f"Gate {i} PASSED in {duration:.1f}s")

        except subprocess.TimeoutExpired:
            print(f"ERROR: Gate {i} timed out after 600 seconds", file=sys.stderr)
            sys.exit(1)

    # XML Parsing
    modules = ["core/communication", "feature/communication", "feature/account", "feature/listingdetail", "app"]
    reports = []
    for mod in modules:
        reports.extend(Path(mod, "build", "test-results").glob("test*UnitTest/TEST-*.xml"))
    reports = sorted(set(reports))

    print(f"\n--- Parsing {len(reports)} Test Reports ---")
    val_res = parse_and_validate_reports(reports, REQUIRED_REPORTS)

    print(f"Test Summary: Total Executed={val_res['total_tests']}, Unique Tests={val_res['unique_tests']}, Failures={val_res['total_failures']}, Errors={val_res['total_errors']}, Skipped={val_res['total_skipped']}")
    print(f"Suites Found ({len(val_res['suites_found'])}): {sorted(val_res['suites_found'])}")

    if not val_res["success"]:
        print(f"ERROR: {val_res['error']}", file=sys.stderr)
        sys.exit(1)

    # Export Evidence
    sources = sorted(set(Path("feature/communication/src/main").rglob("*.kt")) | set(Path("feature/communication/src/test").rglob("*.kt")))
    required = [
        Path("feature/communication/src/main/java/com/fairswap/feature/communication/NotificationsViewModel.kt"),
        Path("feature/communication/src/main/java/com/fairswap/feature/communication/NotificationsScreen.kt"),
        Path("feature/communication/src/test/java/com/fairswap/feature/communication/NotificationsViewModelTest.kt"),
        Path("feature/account/src/main/java/com/fairswap/feature/account/AccountScreen.kt"),
        Path("app/src/main/java/com/fairswap/app/ui/FairSwapApp.kt"),
        Path("app/src/test/java/com/fairswap/app/ui/FairSwapAppTest.kt"),
        Path("scripts/verify_communication_local.py"),
        Path("docs/ANDROID_COMMUNICATION_MILESTONE.md"),
        Path("docs/ANDROID_COMMUNICATION_DEVICE_ACCEPTANCE.md"),
        Path("docs/ANDROID_COMMUNICATION_OVERNIGHT.md"),
    ]
    sources = sorted(set(sources + required))
    optional_app_module = Path("app/src/main/java/com/fairswap/app/di/AppModule.kt")
    if optional_app_module.is_file():
        sources = sorted(set(sources + [optional_app_module]))

    raw = {path: path.read_bytes() for path in (sources + reports)}
    parts = [
        "FairSwap Android communication milestone evidence",
        "Captured UTC: " + datetime.now(timezone.utc).isoformat(),
        "Local source/test evidence only. Device and staging acceptance not run.",
    ]

    source_hashes = {}
    for path in sources:
        data = raw[path]
        text = data.decode("utf-8")
        sha = hashlib.sha256(data).hexdigest()
        source_hashes[str(path)] = sha
        parts.extend([
            f"\n===== SOURCE: {path} =====",
            "SHA-256: " + sha,
            "Lines: " + str(len(text.splitlines())),
            text,
            "===== END SOURCE =====",
        ])

    for path in reports:
        element = ET.fromstring(raw[path])
        suites = [element] if element.tag == "testsuite" else element.findall(".//testsuite")
        parts.extend([
            f"\n===== REPORT: {path} =====",
            "SHA-256: " + hashlib.sha256(raw[path]).hexdigest(),
        ])
        for suite in suites:
            keys = ("name", "timestamp", "tests", "failures", "errors", "skipped")
            parts.append(" ".join(k + "=" + suite.get(k, "") for k in keys))
            for case in suite.findall("testcase"):
                outcome = next((tag.upper() for tag in ("failure", "error", "skipped") if case.find(tag) is not None), "PASS")
                parts.append(f"{case.get('classname', '')}::{case.get('name', '')} | {outcome}")
        parts.append("===== END REPORT =====")

    evidence_file = out_dir / "evidence.txt"
    evidence_file.write_text("\n".join(parts), encoding="utf-8")

    # Machine readable summary
    summary_data = {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "gates": gate_results,
        "test_summary": {
            "total_executed_tests": val_res["total_tests"],
            "unique_tests_count": val_res["unique_tests"],
            "total_failures": val_res["total_failures"],
            "total_errors": val_res["total_errors"],
            "total_skipped": val_res["total_skipped"],
            "required_suites_verified": REQUIRED_REPORTS,
            "all_suites_found": sorted(val_res["suites_found"])
        },
        "source_hashes": source_hashes,
        "evidence_file": str(evidence_file)
    }
    summary_file = out_dir / "summary.json"
    summary_file.write_text(json.dumps(summary_data, indent=2), encoding="utf-8")

    print(f"\nMachine-readable summary written to: {summary_file}")
    print(f"Mechanical evidence exported to: {evidence_file}")
    print("ALL LOCAL VERIFICATION CHECKS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
