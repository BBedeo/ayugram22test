#!/usr/bin/env python3
"""Guard: the update download stays fast and resilient.

* the APK is fetched over several HTTP Range connections and resumes from a saved state;
* a server without Range support falls back to a single stream;
* the release check and the download use the SOCKS5 proxy configured in the app, and fall
  back to a direct connection when the proxy itself fails;
* MTProto proxies (with a secret) are never used for HTTP.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
UPDATER = ROOT / "TMessagesProj/src/main/java/app/exteraless/updater"


def read(name: str) -> str:
    return (UPDATER / name).read_text(encoding="utf-8", errors="ignore")


def main() -> int:
    failures: list[str] = []
    downloader = read("UpdateDownloader.java")
    proxy = read("UpdaterProxy.java")
    github = read("GitHubUpdater.java")

    for needle, why in (
        ('"Range"', "must request byte ranges"),
        ("Protocol.HTTP_1_1", "segments must use separate HTTP/1.1 connections, not one shared HTTP/2 stream"),
        ("static final int SEGMENTS = 16", "must keep several small segments for parallel workers"),
        ("response.code() == 206", "must detect range support"),
        (".state", "must persist progress for resuming"),
        ("single(", "must fall back to one stream without range support"),
        ("SEGMENT_ATTEMPTS", "must retry a failed segment"),
        ("abortCalls()", "an internal failure must not look like a user cancel"),
    ):
        if needle not in downloader:
            failures.append(f"UpdateDownloader {why} (missing `{needle}`)")
    if not re.search(r"run\(direct", downloader):
        failures.append("UpdateDownloader must retry directly when the proxy path fails")

    if "TextUtils.isEmpty(proxy.secret)" not in proxy:
        failures.append("UpdaterProxy must ignore MTProto proxies (non-empty secret)")
    if "Proxy.Type.SOCKS" not in proxy:
        failures.append("UpdaterProxy must route through the SOCKS5 proxy")
    if ".proxySelector(UpdaterProxy.selector())" not in github:
        failures.append("GitHubUpdater.client() must use the app proxy")
    if "Proxy.NO_PROXY" not in github:
        failures.append("GitHubUpdater must keep a direct client as a fallback")
    if github.count("UpdateDownloader.download(") < 2:
        failures.append("both the in-app install and the Downloads path must use UpdateDownloader")
    if "readJson(direct()" not in github:
        failures.append("the release check must fall back to a direct connection when the proxy is dead")
    if "catch (Throwable t)" not in downloader:
        failures.append("a failure inside a segment thread must be reported, not crash the process")
    if "Content-Range" not in downloader or "channel.force(false)" not in downloader:
        failures.append("segments must verify Content-Range and flush bytes before saving the state")
    if "cleanUpdatesDir(" not in github:
        failures.append("old downloads must be cleaned while keeping the resumable partial file")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1
    print("PASS: multi-connection resumable download, app proxy and direct fallback are wired")
    return 0


if __name__ == "__main__":
    sys.exit(main())
