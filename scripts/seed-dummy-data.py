#!/usr/bin/env python3
"""
GymPlan 더미 데이터 시드 스크립트

사용법:
    python3 scripts/seed-dummy-data.py [--reset] [--i-know-this-is-dev] [--url URL]

옵션:
    --reset              기존 demo 계정 삭제 후 재생성
    --i-know-this-is-dev localhost 외 환경에서 실행 허용 (운영 보호 가드 우회)
    --url URL            게이트웨이 URL (기본: http://localhost:8080)
"""

import sys
import argparse
import json
import random
import time
from datetime import datetime, timedelta, timezone
from typing import Optional

try:
    import requests
except ImportError:
    print("❌ 'requests' 패키지가 없습니다. pip3 install requests 실행 후 재시도하세요.")
    sys.exit(1)

# ─────────────────────────────────────────────────────
# 설정
# ─────────────────────────────────────────────────────

DEMO_EMAIL    = "demo@gymplan.test"
DEMO_PASSWORD = "DemoUser2026!"
DEMO_NICKNAME = "데모사용자"

# 루틴 정의 (dayOfWeek: 0=월 ~ 6=일)
PLANS = [
    {"name": "가슴/삼두 루틴",  "description": "월요일 상체 밀기", "dayOfWeek": 0},
    {"name": "등/이두 루틴",    "description": "화요일 상체 당기기", "dayOfWeek": 1},
    {"name": "어깨/하체 루틴",  "description": "목요일 복합 루틴",  "dayOfWeek": 3},
    {"name": "전신 루틴",       "description": "금요일 전신 운동",  "dayOfWeek": 4},
]

# 루틴별 운동 종목 검색 키워드 + 설정
PLAN_EXERCISES = {
    "가슴/삼두 루틴": [
        {"q": "바벨 벤치프레스",              "sets": 4, "reps": 10, "weight": 70.0,  "rest": 120},
        {"q": "인클라인 바벨 벤치프레스",     "sets": 3, "reps": 10, "weight": 60.0,  "rest": 90},
        {"q": "덤벨 플라이",                  "sets": 3, "reps": 12, "weight": 16.0,  "rest": 60},
        {"q": "케이블 트라이셉 푸시다운",     "sets": 3, "reps": 12, "weight": 25.0,  "rest": 60},
        {"q": "바벨 스컬 크러셔",             "sets": 3, "reps": 10, "weight": 30.0,  "rest": 60},
    ],
    "등/이두 루틴": [
        {"q": "바벨 데드리프트",              "sets": 4, "reps": 5,  "weight": 100.0, "rest": 180},
        {"q": "바벨 로우",                    "sets": 4, "reps": 8,  "weight": 70.0,  "rest": 120},
        {"q": "풀업",                         "sets": 3, "reps": 8,  "weight": None,  "rest": 90},
        {"q": "시티드 케이블 로우",           "sets": 3, "reps": 12, "weight": 55.0,  "rest": 60},
        {"q": "바벨 컬",                      "sets": 3, "reps": 12, "weight": 35.0,  "rest": 60},
    ],
    "어깨/하체 루틴": [
        {"q": "바벨 백스쿼트",                "sets": 4, "reps": 8,  "weight": 80.0,  "rest": 150},
        {"q": "레그 프레스",                  "sets": 3, "reps": 12, "weight": 120.0, "rest": 90},
        {"q": "덤벨 숄더 프레스",             "sets": 4, "reps": 10, "weight": 22.0,  "rest": 90},
        {"q": "덤벨 사이드 레터럴 레이즈",   "sets": 3, "reps": 15, "weight": 10.0,  "rest": 60},
        {"q": "레그 컬",                      "sets": 3, "reps": 12, "weight": 45.0,  "rest": 60},
    ],
    "전신 루틴": [
        {"q": "바벨 백스쿼트",                "sets": 3, "reps": 8,  "weight": 75.0,  "rest": 120},
        {"q": "바벨 벤치프레스",              "sets": 3, "reps": 8,  "weight": 65.0,  "rest": 120},
        {"q": "바벨 데드리프트",              "sets": 3, "reps": 5,  "weight": 95.0,  "rest": 150},
        {"q": "풀업",                         "sets": 3, "reps": 6,  "weight": None,  "rest": 90},
        {"q": "덤벨 숄더 프레스",             "sets": 3, "reps": 10, "weight": 20.0,  "rest": 90},
        {"q": "플랭크",                       "sets": 3, "reps": 1,  "weight": None,  "rest": 60},
    ],
}

# 주 4-5회 운동 패턴 (dayOfWeek 기준, 0=월)
TRAINING_DAYS = [0, 1, 3, 4]  # 월/화/목/금


# ─────────────────────────────────────────────────────
# API 클라이언트
# ─────────────────────────────────────────────────────

class GymPlanClient:
    def __init__(self, base_url: str):
        self.base = base_url.rstrip("/")
        self.token: Optional[str] = None
        self.user_id: Optional[int] = None

    def _headers(self) -> dict:
        h = {"Content-Type": "application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    def _req(self, method: str, path: str, **kwargs) -> requests.Response:
        url = f"{self.base}{path}"
        time.sleep(0.25)  # 300 req/min (user limit) 여유 확보
        for attempt in range(4):
            res = getattr(requests, method)(url, headers=self._headers(), timeout=10, **kwargs)
            if res.status_code == 429:
                wait = 2 ** attempt  # 1s, 2s, 4s, 8s
                time.sleep(wait)
                continue
            return res
        return res  # 4회 재시도 후 마지막 응답 반환

    def get(self, path, **kw): return self._req("get", path, **kw)
    def post(self, path, body=None, **kw): return self._req("post", path, json=body, **kw)
    def delete(self, path, **kw): return self._req("delete", path, **kw)

    def raise_for(self, res: requests.Response, ctx: str):
        if res.status_code >= 400:
            try:
                err = res.json().get("error", {})
                raise RuntimeError(f"{ctx} 실패 [{res.status_code}] {err.get('code')}: {err.get('message')}")
            except (ValueError, AttributeError):
                raise RuntimeError(f"{ctx} 실패 [{res.status_code}]: {res.text[:200]}")

    def register(self) -> bool:
        """회원가입. 이미 존재하면 False 반환."""
        res = self.post("/api/v1/auth/register", {
            "email": DEMO_EMAIL, "password": DEMO_PASSWORD, "nickname": DEMO_NICKNAME
        })
        if res.status_code == 409:
            return False
        self.raise_for(res, "회원가입")
        return True

    def login(self):
        res = self.post("/api/v1/auth/login", {"email": DEMO_EMAIL, "password": DEMO_PASSWORD})
        self.raise_for(res, "로그인")
        data = res.json()["data"]
        self.token = data["accessToken"]
        self.user_id = data["userId"]

    def search_exercise(self, q: str) -> Optional[dict]:
        res = self.get("/api/v1/exercises", params={"q": q, "size": 5})
        self.raise_for(res, f"종목 검색({q})")
        content = res.json()["data"]["content"]
        return content[0] if content else None

    def create_plan(self, body: dict) -> int:
        res = self.post("/api/v1/plans", body)
        self.raise_for(res, f"루틴 생성({body['name']})")
        return res.json()["data"]["planId"]

    def add_exercise_to_plan(self, plan_id: int, body: dict):
        res = self.post(f"/api/v1/plans/{plan_id}/exercises", body)
        self.raise_for(res, f"루틴 운동 추가(planId={plan_id})")

    def start_session(self, plan_id: Optional[int], plan_name: Optional[str]) -> str:
        body = {}
        if plan_id:
            body = {"planId": plan_id, "planName": plan_name}
        res = self.post("/api/v1/sessions", body)
        self.raise_for(res, "세션 시작")
        return res.json()["data"]["sessionId"]

    def log_set(self, session_id: str, body: dict):
        res = self.post(f"/api/v1/sessions/{session_id}/sets", body)
        self.raise_for(res, f"세트 기록(session={session_id})")

    def complete_session(self, session_id: str, notes: Optional[str] = None) -> dict:
        res = self.post(f"/api/v1/sessions/{session_id}/complete", {"notes": notes})
        self.raise_for(res, f"세션 완료(session={session_id})")
        return res.json()["data"]

    def cancel_session(self, session_id: str):
        res = self.post(f"/api/v1/sessions/{session_id}/cancel")
        if res.status_code not in (200, 204):
            self.raise_for(res, f"세션 취소(session={session_id})")

    def get_plans(self) -> list:
        res = self.get("/api/v1/plans")
        self.raise_for(res, "루틴 목록")
        return res.json()["data"]

    def get_history(self) -> dict:
        res = self.get("/api/v1/sessions/history", params={"size": 50})
        self.raise_for(res, "히스토리")
        return res.json()["data"]

    def get_analytics_summary(self) -> dict:
        res = self.get("/api/v1/analytics/summary", params={"period": "MONTH"})
        self.raise_for(res, "analytics/summary")
        return res.json()["data"]

    def get_personal_records(self) -> list:
        res = self.get("/api/v1/analytics/personal-records")
        self.raise_for(res, "analytics/personal-records")
        return res.json()["data"]


# ─────────────────────────────────────────────────────
# MongoDB 날짜 패치 (docker exec mongosh)
# ─────────────────────────────────────────────────────

import subprocess

def mongo_update_session_timestamps(session_id: str, started_at: datetime, completed_at: datetime):
    """MongoDB 세션 문서의 startedAt / completedAt / durationSec 을 과거 날짜로 업데이트."""
    started_iso   = started_at.strftime("%Y-%m-%dT%H:%M:%S.000Z")
    completed_iso = completed_at.strftime("%Y-%m-%dT%H:%M:%S.000Z")
    duration_sec  = int((completed_at - started_at).total_seconds())
    js = (
        f'db.workout_sessions.updateOne('
        f'{{"_id":ObjectId("{session_id}")}},'
        f'{{"$set":{{"startedAt":ISODate("{started_iso}"),'
        f'"completedAt":ISODate("{completed_iso}"),'
        f'"durationSec":{duration_sec}}}}}'
        f');'
    )
    result = subprocess.run(
        ["docker", "exec", "gymplan-mongodb", "mongosh",
         "mongodb://gymplan:23WYHbwJOKBwr6s6KJ5aH7vF@localhost:27017/gymplan_workout?authSource=admin",
         "--quiet", "--eval", js],
        capture_output=True, text=True, timeout=10
    )
    if result.returncode != 0:
        print(f"  ⚠️  MongoDB 날짜 패치 실패 ({session_id}): {result.stderr.strip()}")


def mongo_delete_user_sessions(user_id: int):
    """특정 userId의 모든 세션 삭제 (--reset 용)."""
    js = f'db.workout_sessions.deleteMany({{"userId":"{user_id}"}});'
    subprocess.run(
        ["docker", "exec", "gymplan-mongodb", "mongosh",
         "mongodb://gymplan:23WYHbwJOKBwr6s6KJ5aH7vF@localhost:27017/gymplan_workout?authSource=admin",
         "--quiet", "--eval", js],
        capture_output=True, text=True, timeout=10
    )


def es_delete_user_analytics(user_id: str):
    """특정 userId의 ES 세션·세트·PR 문서 삭제 (--reset 용).
    추가로 durationSec < 60 인 잔존 stale 문서도 전체 정리."""
    import urllib.request, urllib.error
    es_base = "http://localhost:9200"

    queries = [
        ('{"query":{"term":{"userId.keyword":"' + user_id + '"}}}',
         ["gymplan-sessions-*", "gymplan-sets-*", "gymplan-personal-records"]),
        ('{"query":{"range":{"durationSec":{"lt":60}}}}',
         ["gymplan-sessions-*"]),
    ]
    for query, patterns in queries:
        for index_pattern in patterns:
            url = f"{es_base}/{index_pattern}/_delete_by_query?conflicts=proceed"
            req = urllib.request.Request(url, data=query.encode(), method="POST",
                                         headers={"Content-Type": "application/json"})
            try:
                urllib.request.urlopen(req, timeout=10)
            except (urllib.error.HTTPError, urllib.error.URLError):
                pass  # 인덱스 없으면 무시


def kafka_replay_sessions(user_id: int, session_records: list[dict]):
    """MongoDB에서 읽은 세션 데이터를 올바른 timestamps로 Kafka 재발행.
    analytics-service가 ES upsert하여 durationSec/dates 정확하게 반영."""
    try:
        from kafka import KafkaProducer
        from kafka.errors import NoBrokersAvailable
        import pymongo as _pymongo
    except ImportError:
        progress("⚠️  kafka-python / pymongo 없음 — Kafka 재발행 건너뜀")
        progress("   pip3 install kafka-python pymongo 후 fix-dummy-duration.py 수동 실행")
        return

    MONGO_URI = "mongodb://gymplan:23WYHbwJOKBwr6s6KJ5aH7vF@localhost:27017/gymplan_workout?authSource=admin"
    KAFKA_BOOTSTRAP = "localhost:9094"

    def _iso(dt: datetime) -> str:
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.strftime("%Y-%m-%dT%H:%M:%S.000Z")

    try:
        producer = KafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP,
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
            request_timeout_ms=10000, max_block_ms=10000,
        )
    except Exception as e:
        progress(f"⚠️  Kafka 연결 실패: {e} — 재발행 건너뜀")
        return

    try:
        mongo = _pymongo.MongoClient(MONGO_URI, serverSelectionTimeoutMS=3000)
        mongo.admin.command("ping")
        db = mongo["gymplan_workout"]
    except Exception as e:
        progress(f"⚠️  MongoDB 직접 연결 실패: {e} — 재발행 건너뜀")
        producer.close()
        return

    now_iso = _iso(datetime.now(timezone.utc))
    count = 0
    for rec in session_records:
        if rec.get("status") != "COMPLETED":
            continue
        sid = rec["sessionId"]
        doc = db.workout_sessions.find_one({"_id": __import__("bson").ObjectId(sid)})
        if not doc:
            continue

        started   = doc["startedAt"]
        completed = doc["completedAt"]
        duration  = int((completed - started).total_seconds())
        muscle_groups = list({ex["muscleGroup"] for ex in doc.get("exercises", []) if ex.get("muscleGroup")})

        # workout.session.completed
        producer.send("workout.session.completed", {
            "eventType": "WORKOUT_SESSION_COMPLETED",
            "sessionId": sid, "userId": doc["userId"],
            "planId": doc.get("planId"), "planName": doc.get("planName"),
            "startedAt": _iso(started), "completedAt": _iso(completed),
            "durationSec": duration,
            "totalVolume": doc.get("totalVolume", 0.0),
            "totalSets": doc.get("totalSets", 0),
            "muscleGroups": muscle_groups, "occurredAt": now_iso,
        })

        # workout.set.logged (세트별)
        total_sets = sum(len(ex.get("sets", [])) for ex in doc.get("exercises", []))
        interval = duration / max(total_sets, 1)
        elapsed = 0.0
        for ex in doc.get("exercises", []):
            for s in ex.get("sets", []):
                w = float(s.get("weightKg", 0.0) or 0.0)
                r = s.get("reps", 10)
                occurred = datetime.fromtimestamp(started.timestamp() + elapsed, tz=timezone.utc)
                producer.send("workout.set.logged", {
                    "eventType": "WORKOUT_SET_LOGGED",
                    "sessionId": sid, "userId": doc["userId"],
                    "exerciseId": str(ex.get("exerciseId", "")),
                    "exerciseName": ex.get("exerciseName", ""),
                    "muscleGroup": ex.get("muscleGroup", ""),
                    "setNo": s.get("setNo", 1),
                    "reps": r, "weightKg": w,
                    "volume": round(w * r, 2),
                    "isSuccess": s.get("isSuccess", True),
                    "occurredAt": _iso(occurred),
                })
                elapsed += interval
        count += 1

    producer.flush()
    producer.close()
    mongo.close()
    progress(f"✓ {count}개 세션 Kafka 재발행 완료 (durationSec/날짜 정확)")

    # analytics-service 소비 대기 후 stale 문서(durationSec < 60) 정리
    time.sleep(10)
    try:
        import urllib.request as _ur, urllib.error as _ue
        stale_q = '{"query":{"range":{"durationSec":{"lt":60}}}}'
        req = _ur.Request(
            "http://localhost:9200/gymplan-sessions-*/_delete_by_query?conflicts=proceed",
            data=stale_q.encode(), method="POST",
            headers={"Content-Type": "application/json"},
        )
        _ur.urlopen(req, timeout=10)
        progress("✓ ES stale 문서(durationSec<60) 정리 완료")
    except Exception:
        pass  # ES 없거나 실패해도 무시


# ─────────────────────────────────────────────────────
# 메인 시드 로직
# ─────────────────────────────────────────────────────

def progress(msg: str):
    print(f"  {msg}", flush=True)


def seed(client: GymPlanClient, reset: bool):
    stats = {"plans": 0, "plan_exercises": 0, "sessions": 0, "sets": 0, "cancelled": 0}
    t_start = time.time()

    # ── 1. 사용자 생성 / 로그인 ──────────────────────
    print("\n▶ 1단계: 사용자")
    existing = not client.register()
    if existing:
        if not reset:
            print(f"  ⛔  {DEMO_EMAIL} 이미 존재합니다.")
            print("     --reset 플래그를 사용하면 기존 데이터를 삭제하고 재생성합니다.")
            sys.exit(1)
        progress(f"기존 사용자 발견 — 로그인 후 데이터 초기화 (--reset)")
    else:
        progress(f"회원가입 완료: {DEMO_EMAIL}")

    client.login()
    progress(f"로그인 성공 (userId={client.user_id})")

    if existing and reset:
        _reset_user_data(client, stats)

    # ── 2. 종목 ID 조회 ─────────────────────────────
    print("\n▶ 2단계: 종목 ID 조회")
    exercise_map: dict[str, dict] = {}  # q → {exerciseId, exerciseName, muscleGroup}
    all_qs = {ex["q"] for exs in PLAN_EXERCISES.values() for ex in exs}
    for q in sorted(all_qs):
        ex = client.search_exercise(q)
        if ex:
            exercise_map[q] = {
                "exerciseId":   ex["exerciseId"],
                "exerciseName": ex["name"],
                "muscleGroup":  ex["muscleGroup"],
            }
            progress(f"✓ {q} → id={ex['exerciseId']}")
        else:
            progress(f"⚠️  '{q}' 검색 결과 없음 — 건너뜀")

    # ── 3. 루틴 생성 ─────────────────────────────────
    print("\n▶ 3단계: 루틴 생성")
    plan_map: dict[str, int] = {}  # plan name → planId
    for p in PLANS:
        pid = client.create_plan(p)
        plan_map[p["name"]] = pid
        stats["plans"] += 1
        progress(f"✓ {p['name']} (planId={pid}, dayOfWeek={p['dayOfWeek']})")

        # 운동 추가
        for order, ex_cfg in enumerate(PLAN_EXERCISES[p["name"]]):
            q = ex_cfg["q"]
            if q not in exercise_map:
                continue
            ex = exercise_map[q]
            body = {
                "exerciseId":   ex["exerciseId"],
                "exerciseName": ex["exerciseName"],
                "muscleGroup":  ex["muscleGroup"],
                "orderIndex":   order,
                "targetSets":   ex_cfg["sets"],
                "targetReps":   ex_cfg["reps"],
                "targetWeightKg": ex_cfg["weight"],
                "restSeconds":  ex_cfg["rest"],
            }
            client.add_exercise_to_plan(pid, body)
            stats["plan_exercises"] += 1
        progress(f"  └─ 운동 {len(PLAN_EXERCISES[p['name']])}개 추가")

    # ── 4. 세션 생성 (지난 30일) ─────────────────────
    print("\n▶ 4단계: 지난 30일 세션 생성")
    today = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    session_records: list[dict] = []  # {sessionId, started_at, completed_at}

    # 루틴 이름 → planId, dayOfWeek 매핑
    plan_by_day: dict[int, dict] = {}
    for p in PLANS:
        plan_by_day[p["dayOfWeek"]] = {"planId": plan_map[p["name"]], "planName": p["name"]}

    # 30일치 날짜 생성
    for days_ago in range(30, 0, -1):
        day = today - timedelta(days=days_ago)
        dow = (day.weekday())  # 0=Mon .. 6=Sun

        if dow not in TRAINING_DAYS:
            continue
        # 70% 확률로 운동 (사실적)
        if random.random() > 0.75:
            continue

        plan_info = plan_by_day.get(dow)
        started_at = day.replace(
            hour=random.randint(7, 20),
            minute=random.choice([0, 15, 30, 45])
        )

        # 10% 확률로 취소 세션 생성
        is_cancelled = (random.random() < 0.10)

        try:
            sid = client.start_session(
                plan_info["planId"] if plan_info else None,
                plan_info["planName"] if plan_info else None,
            )
        except RuntimeError as e:
            # 이미 진행 중인 세션이 있으면 스킵
            if "SESSION_ALREADY_ACTIVE" in str(e):
                progress(f"⚠️  {day.date()} 진행 중 세션 충돌 — 건너뜀")
                continue
            raise

        if is_cancelled:
            client.cancel_session(sid)
            mongo_update_session_timestamps(sid, started_at, started_at + timedelta(minutes=5))
            session_records.append({"sessionId": sid, "started_at": started_at, "status": "CANCELLED"})
            stats["cancelled"] += 1
            progress(f"  {day.date()} CANCELLED (dow={dow})")
            continue

        # 세트 기록: 점진적 증가 반영
        progress_factor = (30 - days_ago) / 30  # 0.0 → 1.0

        if plan_info:
            exercises_for_session = PLAN_EXERCISES[plan_info["planName"]]
        else:
            # 자유 운동 (랜덤 종목 3개)
            exercises_for_session = random.sample(
                [ex for exs in PLAN_EXERCISES.values() for ex in exs], k=3
            )

        duration_min = 0
        for ex_cfg in exercises_for_session:
            q = ex_cfg["q"]
            if q not in exercise_map:
                continue
            ex = exercise_map[q]
            n_sets = ex_cfg["sets"] + random.randint(-1, 1)
            n_sets = max(2, min(n_sets, 6))

            base_weight = ex_cfg["weight"]

            for set_no in range(1, n_sets + 1):
                # 점진적 무게 증가 (최대 +10%)
                if base_weight is not None:
                    weight = round(base_weight * (1 + 0.10 * progress_factor), 1)
                    # 2.5kg 단위 반올림
                    weight = round(weight / 2.5) * 2.5
                else:
                    weight = None

                reps = ex_cfg["reps"] + random.randint(-2, 2)
                reps = max(1, reps)
                # 마지막 세트는 가끔 실패
                is_success = not (set_no == n_sets and random.random() < 0.20)

                client.log_set(sid, {
                    "exerciseId":   str(ex["exerciseId"]),
                    "exerciseName": ex["exerciseName"],
                    "muscleGroup":  ex["muscleGroup"],
                    "setNo":        set_no,
                    "reps":         reps,
                    "weightKg":     weight if weight is not None else 70.0,  # 맨몸 운동 기본 체중
                    "isSuccess":    is_success,
                })
                stats["sets"] += 1
                duration_min += ex_cfg["rest"] // 60 + 1

        completed_at = started_at + timedelta(minutes=duration_min + random.randint(-5, 15))
        client.complete_session(sid)
        # MongoDB 타임스탬프 + durationSec 동시 패치
        mongo_update_session_timestamps(sid, started_at, completed_at)
        session_records.append({
            "sessionId":    sid,
            "started_at":  started_at,
            "completed_at": completed_at,
            "status":       "COMPLETED",
        })
        stats["sessions"] += 1
        progress(f"  {day.date()} ✓ {plan_info['planName'] if plan_info else '자유운동'} ({len(exercises_for_session)}종목, dow={dow})")

    # ── 4-b. Kafka 이벤트 재발행 (올바른 timestamps/durationSec 으로) ─────────
    print("\n▶ 4-b단계: Kafka 이벤트 재발행 (정확한 timestamps)")
    kafka_replay_sessions(client.user_id, session_records)

    return stats, time.time() - t_start


def _reset_user_data(client: GymPlanClient, stats: dict):
    """기존 루틴·세션·ES 인덱스 삭제."""
    progress("  기존 루틴 삭제 중...")
    plans = client.get_plans()
    for p in plans:
        res = client._req("delete", f"/api/v1/plans/{p['planId']}")
        if res.status_code not in (200, 204):
            progress(f"  ⚠️  planId={p['planId']} 삭제 실패 (무시)")

    progress(f"  루틴 {len(plans)}개 삭제 완료")
    progress("  MongoDB 세션 삭제 중...")
    mongo_delete_user_sessions(client.user_id)
    progress("  MongoDB 세션 삭제 완료")
    progress("  Elasticsearch 세션/세트 인덱스 삭제 중...")
    es_delete_user_analytics(str(client.user_id))
    progress("  Elasticsearch 인덱스 삭제 완료")


def verify(client: GymPlanClient):
    print("\n▶ 5단계: 데이터 검증")
    # 잠시 대기 (Kafka → ES 인덱싱 시간)
    time.sleep(3)

    plans = client.get_plans()
    progress(f"GET /plans → {len(plans)}개 루틴")
    for p in plans:
        progress(f"  · {p['name']} (exerciseCount={p.get('exerciseCount', '?')})")

    history = client.get_history()
    total = history.get("totalElements", len(history.get("content", [])))
    progress(f"GET /sessions/history → {total}개 세션")

    try:
        summary = client.get_analytics_summary()
        progress(f"GET /analytics/summary (MONTH) → sessions={summary.get('totalSessions')}, "
                 f"volume={summary.get('totalVolume')}, muscle={summary.get('mostTrainedMuscle')}")
    except RuntimeError as e:
        progress(f"⚠️  analytics/summary: {e}")

    try:
        prs = client.get_personal_records()
        progress(f"GET /analytics/personal-records → {len(prs)}개 종목 PR")
        for pr in prs[:3]:
            progress(f"  · {pr.get('exerciseName')}: {pr.get('maxWeightKg')}kg × {pr.get('maxReps')}회")
    except RuntimeError as e:
        progress(f"⚠️  analytics/personal-records: {e}")


# ─────────────────────────────────────────────────────
# 엔트리포인트
# ─────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="GymPlan 더미 데이터 시드")
    parser.add_argument("--reset",              action="store_true", help="기존 demo 데이터 초기화")
    parser.add_argument("--i-know-this-is-dev", action="store_true", help="localhost 외 환경 실행 허용")
    parser.add_argument("--url",                default="http://localhost:8080", help="게이트웨이 URL")
    args = parser.parse_args()

    # ── 운영 보호 가드 ────────────────────────────────
    is_local = "localhost" in args.url or "127.0.0.1" in args.url
    if not is_local and not args.i_know_this_is_dev:
        print("❌ 운영 보호 가드: 비 localhost 환경에서는 --i-know-this-is-dev 플래그가 필요합니다.")
        print(f"   현재 URL: {args.url}")
        sys.exit(1)

    random.seed(42)  # 재현 가능한 시드

    print("=" * 55)
    print("  GymPlan 더미 데이터 시드")
    print(f"  URL:   {args.url}")
    print(f"  계정:  {DEMO_EMAIL} / {DEMO_PASSWORD}")
    print(f"  모드:  {'--reset (데이터 초기화)' if args.reset else '신규 생성'}")
    print("=" * 55)

    client = GymPlanClient(args.url)

    try:
        stats, elapsed = seed(client, reset=args.reset)
        verify(client)
    except RuntimeError as e:
        print(f"\n❌ 오류 발생: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

    print("\n" + "=" * 55)
    print("  ✅ 시드 완료")
    print(f"  루틴:       {stats['plans']}개")
    print(f"  루틴 운동:  {stats['plan_exercises']}개")
    print(f"  세션 완료:  {stats['sessions']}개")
    print(f"  취소 세션:  {stats['cancelled']}개")
    print(f"  세트:       {stats['sets']}개")
    print(f"  소요 시간:  {elapsed:.1f}초")
    print("=" * 55)
    print(f"\n  브라우저에서 {DEMO_EMAIL} / {DEMO_PASSWORD} 로 로그인하세요.")


if __name__ == "__main__":
    main()
