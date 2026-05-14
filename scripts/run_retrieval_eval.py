#!/usr/bin/env python3
"""
批量调用 dong-rag 检索检测与（可选）助手编排评测，输出 JSON + 简历用中文摘要。

依赖：仅 Python 3 标准库。

示例：
  set DONG_RAG_BASE_URL=http://localhost:8080
  set DONG_RAG_TOKEN=<admin 登录后的 token>
  python scripts/run_retrieval_eval.py retrieval --dataset eval/retrieval/datasets/my.json

  python scripts/run_retrieval_eval.py assistant --group-id 1
  python scripts/run_retrieval_eval.py assistant --group-id 1 --template-id INTERNAL_KB_MULTI

  python scripts/run_retrieval_eval.py seed-dataset
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from typing import Any


def _env(name: str, default: str | None = None) -> str | None:
    v = os.environ.get(name)
    return v if v is not None and v != "" else default


def _slug(s: str) -> str:
    s = re.sub(r"[^\w\-]+", "_", s.strip(), flags=re.UNICODE)
    return s.strip("_") or "dataset"


def _post_json(url: str, token: str, body: dict[str, Any], timeout: int) -> dict[str, Any]:
    payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {e.code} {url}\n{err_body}") from e
    except urllib.error.URLError as e:
        raise SystemExit(f"请求失败: {e.reason!r} ({url})") from e
    try:
        wrapper = json.loads(raw)
    except json.JSONDecodeError as e:
        raise SystemExit(f"非 JSON 响应: {raw[:500]}") from e
    if wrapper.get("code") != 0:
        raise SystemExit(f"业务错误 code={wrapper.get('code')} message={wrapper.get('message')!r}")
    data = wrapper.get("data")
    if not isinstance(data, dict):
        raise SystemExit("响应 data 缺失或非对象")
    return data


def _get_json(url: str, token: str, timeout: int) -> dict[str, Any]:
    req = urllib.request.Request(url, method="GET")
    req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {e.code} {url}\n{err_body}") from e
    except urllib.error.URLError as e:
        raise SystemExit(f"请求失败: {e.reason!r} ({url})") from e
    try:
        wrapper = json.loads(raw)
    except json.JSONDecodeError as e:
        raise SystemExit(f"非 JSON 响应: {raw[:500]}") from e
    if wrapper.get("code") != 0:
        raise SystemExit(f"业务错误 code={wrapper.get('code')} message={wrapper.get('message')!r}")
    data = wrapper.get("data")
    if not isinstance(data, dict):
        raise SystemExit("响应 data 缺失或非对象")
    return data


def _poll_ingest_task(base: str, token: str, job_id: int, total_timeout: int) -> dict[str, Any]:
    url = f"{base}/rag/ingest/task/{job_id}"
    deadline = time.monotonic() + total_timeout
    while time.monotonic() < deadline:
        data = _get_json(url, token, 60)
        js = data.get("jobStatus")
        ds = data.get("documentStatus")
        if js == "FAILED":
            raise SystemExit(
                f"入库失败 jobId={job_id} documentStatus={ds!r} failureReason={data.get('failureReason')!r} "
                f"lastError={data.get('lastError')!r}"
            )
        if js == "SUCCESS" and ds == "READY":
            return data
        time.sleep(1.5)
    raise SystemExit(f"等待入库超时（{total_timeout}s）jobId={job_id}，请确认异步入库与 MinIO/向量/ES 可用。")


def cmd_seed_dataset(args: argparse.Namespace) -> None:
    """按 seed_corpus 逐条文本入库，生成带金标（documentId + chunk 0）的 example.json。"""
    base = (args.base_url or _env("DONG_RAG_BASE_URL") or "").rstrip("/")
    token = args.token or _env("DONG_RAG_TOKEN")
    if not base or not token:
        sys.exit("请设置 --base-url / --token 或环境变量 DONG_RAG_BASE_URL、DONG_RAG_TOKEN。")

    corpus_path = os.path.abspath(args.corpus)
    with open(corpus_path, encoding="utf-8") as f:
        corpus = json.load(f)
    meta_c = corpus.get("meta") or {}
    items = corpus.get("items")
    if not isinstance(items, list) or not items:
        sys.exit("seed_corpus 格式错误：需要 items 数组。")

    group_id = int(args.group_id) if args.group_id is not None else int(meta_c.get("defaultGroupId") or 1)
    poll_timeout = int(args.poll_timeout)
    cases: list[dict[str, Any]] = []

    my_groups: list | None = None
    try:
        my_groups = _get_json(f"{base}/group/my/list", token, 60)
    except SystemExit as e:
        print(f"拉取已加入组列表失败: {e}", file=sys.stderr)
        my_groups = None

    if isinstance(my_groups, list):
        if my_groups:
            print("当前账号已加入的知识组（GET /group/my/list）：")
            for g in my_groups:
                gid = g.get("id")
                print(f"  id={gid}  code={g.get('groupCode')!r}  name={g.get('groupName')!r}")
            my_ids = {int(g["id"]) for g in my_groups if g.get("id") is not None}
            if group_id not in my_ids:
                print(
                    f"\n提示: 目标 groupId={group_id} 不在上述列表中。"
                    "非 admin 用户须先加入该组；本脚本默认会在入库前尝试 POST /group/join。"
                )
        else:
            print("当前账号尚未加入任何知识组（列表为空）；将尝试 POST /group/join。\n")
    else:
        print("（未能解析组列表响应；若后续报权限错误请检查 token）\n")

    if args.join_first:
        try:
            _post_json(f"{base}/group/join", token, {"groupId": group_id}, timeout=60)
            print(f"已请求加入组 groupId={group_id}（若已在组内则无变化）\n")
        except SystemExit as e:
            print(f"加入组失败（可忽略若你已是 admin 或已在组内）: {e}\n", file=sys.stderr)

    for idx, it in enumerate(items):
        if not isinstance(it, dict):
            continue
        fn = it.get("fileName")
        content = it.get("content")
        question = it.get("question")
        if not fn or not content or not question:
            sys.exit(f"items[{idx}] 缺少 fileName / content / question")
        body = {"groupId": group_id, "fileName": str(fn), "content": str(content)}
        data = _post_json(f"{base}/rag/ingest/text", token, body, timeout=120)
        job_id = data.get("jobId")
        doc_id = data.get("documentId")
        if not job_id:
            sys.exit(f"入库响应缺少 jobId: {data!r}")
        print(f"[{idx + 1}/{len(items)}] 已提交入库 jobId={job_id} documentId={doc_id} fileName={fn}")
        final = _poll_ingest_task(base, token, int(job_id), poll_timeout)
        final_doc = final.get("documentId")
        if final_doc is None:
            sys.exit(f"轮询结束仍无 documentId: {final!r}")
        cases.append(
            {
                "question": str(question).strip(),
                "goldDocumentId": int(final_doc),
                "goldChunkIndex": 0,
            }
        )

    iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    out_bundle = {
        "meta": {
            "datasetName": str(args.dataset_name or "dong-rag-eval-demo"),
            "groupId": group_id,
            "topK": int(args.top_k),
            "includeRerankComparison": bool(args.include_rerank_comparison),
            "notes": (
                f"由 seed-dataset 根据 {os.path.basename(corpus_path)} 自动生成于 {iso}；"
                f"每条短文单独入库，金标 chunk_index 固定为 0（单块）。"
            ),
        },
        "cases": cases,
    }
    out_path = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out_bundle, f, ensure_ascii=False, indent=2)
    print(f"\n已写入评测集（含金标）: {out_path}")
    print("下一步: python scripts/run_retrieval_eval.py retrieval -d {0}".format(out_path))


def _weighted_merge_retrieval(batch_data: list[dict[str, Any]]) -> dict[str, Any]:
    """按 labeledCount 对 meanHitAt1 / meanHitAtK / mrr 及可选 baseline 做加权合并。"""
    total_labeled = 0
    sum_h1 = sum_hk = sum_rr = 0.0
    baseline_batches = 0
    sum_h1b = sum_hkb = sum_rrb = 0.0
    total_labeled_baseline = 0

    for r in batch_data:
        if not r:
            continue
        lc = int(r.get("labeledCount") or 0)
        if lc <= 0:
            continue
        total_labeled += lc
        m1, mk, mrr = r.get("meanHitAt1"), r.get("meanHitAtK"), r.get("mrr")
        if m1 is not None:
            sum_h1 += float(m1) * lc
        if mk is not None:
            sum_hk += float(mk) * lc
        if mrr is not None:
            sum_rr += float(mrr) * lc

        mb1 = r.get("meanHitAt1Baseline")
        if mb1 is not None:
            baseline_batches += 1
            mkb = r.get("meanHitAtKBaseline")
            mbrr = r.get("mrrBaseline")
            sum_h1b += float(mb1) * lc
            sum_hkb += float(mkb) * lc if mkb is not None else 0.0
            sum_rrb += float(mbrr) * lc if mbrr is not None else 0.0
            total_labeled_baseline += lc

    merged: dict[str, Any] = {
        "labeledCount": total_labeled,
        "meanHitAt1": (sum_h1 / total_labeled) if total_labeled else None,
        "meanHitAtK": (sum_hk / total_labeled) if total_labeled else None,
        "mrr": (sum_rr / total_labeled) if total_labeled else None,
    }
    if baseline_batches > 0 and total_labeled_baseline > 0:
        merged["meanHitAt1Baseline"] = sum_h1b / total_labeled_baseline
        merged["meanHitAtKBaseline"] = sum_hkb / total_labeled_baseline
        merged["mrrBaseline"] = sum_rrb / total_labeled_baseline
        mrr = merged.get("mrr")
        mrr_b = merged.get("mrrBaseline")
        if mrr is not None and mrr_b is not None:
            merged["mrrDeltaVsBaseline"] = float(mrr) - float(mrr_b)
    return merged


def _resume_snippet_retrieval(
    dataset_name: str,
    top_k: int,
    merged: dict[str, Any],
    env_note: str,
) -> str:
    n = merged.get("labeledCount") or 0
    h1 = merged.get("meanHitAt1")
    hk = merged.get("meanHitAtK")
    mrr = merged.get("mrr")
    parts = [
        f"自建金标检索评测集「{dataset_name}」共 {n} 条有效标注，Top-K={top_k}，"
        f"平均 Hit@1={_fmt_ratio(h1)}、Hit@K={_fmt_ratio(hk)}、MRR={_fmt_float(mrr)}"
    ]
    if merged.get("mrrBaseline") is not None:
        parts.append(
            f"；重排前 MRR={_fmt_float(merged.get('mrrBaseline'))}"
            f"（ΔMRR={_fmt_float(merged.get('mrrDeltaVsBaseline'))}）"
        )
    parts.append(f"。{env_note}")
    return "".join(parts)


def _fmt_ratio(x: Any) -> str:
    if x is None:
        return "—"
    return f"{float(x):.2f}"


def _fmt_float(x: Any) -> str:
    if x is None:
        return "—"
    return f"{float(x):.4f}"


def cmd_retrieval(args: argparse.Namespace) -> None:
    base = (args.base_url or _env("DONG_RAG_BASE_URL") or "").rstrip("/")
    token = args.token or _env("DONG_RAG_TOKEN")
    if not base or not token:
        sys.exit("请设置 --base-url / --token 或环境变量 DONG_RAG_BASE_URL、DONG_RAG_TOKEN（admin 账号 token）。")

    path = args.dataset
    with open(path, encoding="utf-8") as f:
        bundle = json.load(f)
    meta = bundle.get("meta") or {}
    cases = bundle.get("cases") or []
    if not isinstance(meta, dict) or not isinstance(cases, list) or not cases:
        sys.exit("数据集格式错误：需要顶层 meta 与 cases 数组。")

    group_id = meta.get("groupId")
    if group_id is None:
        sys.exit("meta.groupId 必填。")
    top_k = meta.get("topK")
    if top_k is None:
        top_k = 5
    rerank_cmp = bool(meta.get("includeRerankComparison"))
    dataset_name = str(meta.get("datasetName") or "dataset")

    batch_size = max(1, int(args.batch_size))
    batches: list[list[dict[str, Any]]] = []
    for i in range(0, len(cases), batch_size):
        batches.append(cases[i : i + batch_size])

    url = f"{base}/rag/detect/retrieval"
    timeout = int(args.timeout)
    batch_responses: list[dict[str, Any]] = []
    all_details: list[dict[str, Any]] = []

    for bi, batch in enumerate(batches):
        body = {
            "groupId": group_id,
            "topK": top_k,
            "cases": batch,
            "includeRerankComparison": rerank_cmp,
        }
        data = _post_json(url, token, body, timeout=timeout)
        batch_responses.append(data)
        details = data.get("details") if isinstance(data, dict) else None
        if isinstance(details, list):
            all_details.extend(details)
        print(f"[batch {bi + 1}/{len(batches)}] labeledCount={data.get('labeledCount')} caseCount={data.get('caseCount')}")

    merged = _weighted_merge_retrieval(batch_responses)
    iso_now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H%M%SZ")
    slug = _slug(dataset_name)
    out_dir = os.path.abspath(args.out_dir)
    os.makedirs(out_dir, exist_ok=True)
    out_json = os.path.join(out_dir, f"retrieval-{slug}-{iso_now}.json")

    assistant_block: dict[str, Any] | None = None
    if args.assistant_group_id is not None:
        assistant_block = _run_assistant_eval(
            base,
            token,
            int(args.assistant_group_id),
            args.assistant_template_id,
            int(args.timeout),
        )

    payload: dict[str, Any] = {
        "generatedAt": iso_now,
        "baseUrl": base,
        "datasetPath": os.path.abspath(path),
        "meta": meta,
        "batchSize": batch_size,
        "batches": batch_responses,
        "mergedRetrieval": merged,
        "details": all_details,
    }
    if assistant_block is not None:
        payload["assistantEval"] = assistant_block

    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    env_note = "离线/测试环境指标，简历中建议注明数据来源与日期。"
    resume_r = _resume_snippet_retrieval(dataset_name, int(top_k), merged, env_note)
    lines = [resume_r]
    if assistant_block:
        lines.append(_resume_snippet_assistant(assistant_block, env_note))

    snippet_path = os.path.join(out_dir, f"resume-snippet-{slug}-{iso_now}.txt")
    with open(snippet_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    print("\n--- 合并指标（检索） ---")
    print(json.dumps(merged, ensure_ascii=False, indent=2))
    print(f"\n已写入:\n  {out_json}\n  {snippet_path}")
    print("\n--- 简历摘要 ---")
    for line in lines:
        print(line)


def _run_assistant_eval(
    base: str,
    token: str,
    group_id: int,
    template_id: str | None,
    timeout: int,
) -> dict[str, Any]:
    q = f"groupId={group_id}"
    if template_id:
        q += f"&templateId={urllib.parse.quote(template_id)}"
    url = f"{base}/assistant/eval/complaint?{q}"
    data = _post_assistant_get(url, token, timeout)
    return {"request": {"groupId": group_id, "templateId": template_id}, "data": data}


def _post_assistant_get(url: str, token: str, timeout: int) -> dict[str, Any]:
    req = urllib.request.Request(url, method="POST")
    req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {e.code} {url}\n{err_body}") from e
    except urllib.error.URLError as e:
        raise SystemExit(f"请求失败: {e.reason!r} ({url})") from e
    try:
        wrapper = json.loads(raw)
    except json.JSONDecodeError as e:
        raise SystemExit(f"非 JSON 响应: {raw[:500]}") from e
    if wrapper.get("code") != 0:
        raise SystemExit(f"业务错误 code={wrapper.get('code')} message={wrapper.get('message')!r}")
    data = wrapper.get("data")
    if not isinstance(data, dict):
        raise SystemExit("响应 data 缺失或非对象")
    return data


def _resume_snippet_assistant(block: dict[str, Any], env_note: str) -> str:
    d = block.get("data") or {}
    cr = d.get("caseCount")
    hr = d.get("handoffRate")
    av = d.get("avgSubTaskCount")
    tid = d.get("templateId")
    return (
        f"助手编排回归（templateId={tid}，{cr} 条样例）："
        f"handoffRate={_fmt_ratio(hr)}，avgSubTaskCount={_fmt_float(av)}。{env_note}"
    )


def cmd_assistant(args: argparse.Namespace) -> None:
    base = (args.base_url or _env("DONG_RAG_BASE_URL") or "").rstrip("/")
    token = args.token or _env("DONG_RAG_TOKEN")
    if not base or not token:
        sys.exit("请设置 --base-url / --token 或环境变量 DONG_RAG_BASE_URL、DONG_RAG_TOKEN。")

    q = f"groupId={args.group_id}"
    if args.template_id:
        q += f"&templateId={urllib.parse.quote(args.template_id)}"
    url = f"{base}/assistant/eval/complaint?{q}"
    data = _post_assistant_get(url, token, int(args.timeout))

    iso_now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H%M%SZ")
    out_dir = os.path.abspath(args.out_dir)
    os.makedirs(out_dir, exist_ok=True)
    out_json = os.path.join(out_dir, f"assistant-eval-{args.group_id}-{iso_now}.json")
    payload = {"generatedAt": iso_now, "baseUrl": base, "data": data}
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    snippet_path = os.path.join(out_dir, f"resume-snippet-assistant-{args.group_id}-{iso_now}.txt")
    env_note = "离线/测试环境指标。"
    line = _resume_snippet_assistant({"data": data}, env_note)
    with open(snippet_path, "w", encoding="utf-8") as f:
        f.write(line + "\n")

    print(json.dumps(data, ensure_ascii=False, indent=2))
    print(f"\n已写入:\n  {out_json}\n  {snippet_path}")
    print("\n--- 简历摘要 ---")
    print(line)


def main() -> None:
    p = argparse.ArgumentParser(description="dong-rag 检索/助手评测跑批")
    p.add_argument("--base-url", default=None, help="默认读 DONG_RAG_BASE_URL")
    p.add_argument("--token", default=None, help="默认读 DONG_RAG_TOKEN（Sa-Token，与前端 Authorization 一致）")
    sub = p.add_subparsers(dest="cmd", required=True)

    pr = sub.add_parser("retrieval", help="跑检索金标评测集")
    pr.add_argument("--dataset", "-d", required=True, help="评测 JSON 路径（含 meta + cases）")
    pr.add_argument("--batch-size", type=int, default=20)
    pr.add_argument("--out-dir", default="eval/results", help="输出目录")
    pr.add_argument("--timeout", type=int, default=300, help="单请求超时秒数")
    pr.add_argument(
        "--assistant-group-id",
        type=int,
        default=None,
        help="若指定，则在同一次结果 JSON 中追加 POST /assistant/eval/complaint",
    )
    pr.add_argument(
        "--assistant-template-id",
        default=None,
        help="与 --assistant-group-id 联用，对应 AgentTemplateId 枚举名",
    )

    pa = sub.add_parser("assistant", help="仅跑助手编排评测")
    pa.add_argument("--group-id", type=int, required=True)
    pa.add_argument("--template-id", default=None)
    pa.add_argument("--out-dir", default="eval/results")
    pa.add_argument("--timeout", type=int, default=600)

    ps = sub.add_parser("seed-dataset", help="按 seed_corpus 自动入库并生成带金标的 example.json")
    ps.add_argument(
        "--corpus",
        default="eval/retrieval/datasets/seed_corpus.json",
        help="语料 JSON（items: fileName, content, question）",
    )
    ps.add_argument(
        "--out",
        default="eval/retrieval/datasets/example.json",
        help="输出的评测集路径（覆盖写入）",
    )
    ps.add_argument("--group-id", type=int, default=None, help="默认用 corpus.meta.defaultGroupId 或 1")
    ps.add_argument("--dataset-name", default="dong-rag-eval-demo", dest="dataset_name")
    ps.add_argument("--top-k", type=int, default=5, dest="top_k")
    ps.add_argument("--include-rerank-comparison", action="store_true", dest="include_rerank_comparison")
    ps.add_argument("--poll-timeout", type=int, default=300, help="单条入库最长等待秒数")
    ps.add_argument(
        "--join-first",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="入库前 POST /group/join（默认开启；非 admin 未入组时避免 40101）",
    )

    args = p.parse_args()
    if args.cmd == "assistant":
        cmd_assistant(args)
    elif args.cmd == "seed-dataset":
        cmd_seed_dataset(args)
    else:
        cmd_retrieval(args)


if __name__ == "__main__":
    main()
