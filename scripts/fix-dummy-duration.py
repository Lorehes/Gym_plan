#!/usr/bin/env python3
"""
더미 데이터 durationSec 및 ES 색인 수정 스크립트.

문제:
  1. MongoDB workout_sessions.durationSec = 3~5초 (API 호출 시간)
     → completedAt - startedAt 이 이미 30~90분으로 패치됐으므로 durationSec 만 갱신
  2. Elasticsearch gymplan-sessions-* 문서의 timestamps / durationSec 가 오래된 Kafka 이벤트 기준
     → workout.session.completed 이벤트 재발행 → analytics-service가 upsert

용도:
  python3 scripts/fix-dummy-duration.py

전제 조건:
  - gymplan-mongodb 컨테이너 실행 중
  - gymplan-kafka  컨테이너 실행 중 (외부 리스너: localhost:9094)
  - analytics-service 실행 중 (localhost:8085)
  - pip3 install pymongo kafka-python
"""

import json
import time
import sys
from datetime import datetime, timezone

try:
    from pymongo import MongoClient
    from kafka import KafkaProducer
    from kafka.errors import NoBrokersAvailable
except ImportError as e:
    print(f"의존성 없음: {e}")
    print("pip3 install pymongo kafka-python")
    sys.exit(1)

MONGO_URI = "mongodb://gymplan:23WYHbwJOKBwr6s6KJ5aH7vF@localhost:27017/gymplan_workout?authSource=admin"
KAFKA_BOOTSTRAP = "localhost:9094"
TOPIC_SESSION  = "workout.session.completed"
TOPIC_SET      = "workout.set.logged"
DEMO_USER_ID   = "3"   # seed-dummy-data.py 의 demo userId

# ──────────────────────────────────────────────────────────────────────────────
# 1단계: MongoDB durationSec 보정
# ──────────────────────────────────────────────────────────────────────────────

def fix_mongo_duration(db) -> list[dict]:
    """각 세션의 durationSec을 completedAt - startedAt 으로 재계산하고 저장."""
    sessions = list(db.workout_sessions.find(
        {"userId": DEMO_USER_ID, "status": "COMPLETED"},
        sort=[("startedAt", 1)]
    ))

    print(f"\n▶ 1단계: MongoDB durationSec 보정 ({len(sessions)}개 세션)")
    updated = 0
    for s in sessions:
        started   = s["startedAt"]
        completed = s["completedAt"]
        duration_sec = int((completed - started).total_seconds())

        if duration_sec != s.get("durationSec"):
            db.workout_sessions.update_one(
                {"_id": s["_id"]},
                {"$set": {"durationSec": duration_sec}}
            )
            s["durationSec"] = duration_sec
            updated += 1

        print(f"  {started.strftime('%Y-%m-%d')} {s.get('planName','')} "
              f"→ {duration_sec}초 ({duration_sec//60}분) [{s['totalSets']}세트]")

    print(f"  ✓ {updated}개 업데이트 완료")
    return sessions


# ──────────────────────────────────────────────────────────────────────────────
# 2단계: Kafka 이벤트 재발행
# ──────────────────────────────────────────────────────────────────────────────

def _iso(dt: datetime) -> str:
    """datetime → ISO-8601 UTC 문자열 (Instant 호환)."""
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%S.000Z")


def replay_kafka_events(producer: KafkaProducer, sessions: list[dict]):
    """세션별 workout.session.completed 및 workout.set.logged 이벤트 재발행."""
    print(f"\n▶ 2단계: Kafka 이벤트 재발행")

    session_count = 0
    set_count = 0

    for s in sessions:
        sid          = str(s["_id"])
        started_at   = s["startedAt"]
        completed_at = s["completedAt"]
        duration_sec = int((completed_at - started_at).total_seconds())
        now_iso      = _iso(datetime.now(timezone.utc))

        # ── workout.session.completed ────────────────────────────────────────
        muscle_groups = list({
            ex["muscleGroup"]
            for ex in s.get("exercises", [])
            if ex.get("muscleGroup")
        })

        session_event = {
            "eventType":   "WORKOUT_SESSION_COMPLETED",
            "sessionId":   sid,
            "userId":      s["userId"],
            "planId":      s.get("planId"),
            "planName":    s.get("planName"),
            "startedAt":   _iso(started_at),
            "completedAt": _iso(completed_at),
            "durationSec": duration_sec,
            "totalVolume": s.get("totalVolume", 0.0),
            "totalSets":   s.get("totalSets", 0),
            "muscleGroups": muscle_groups,
            "occurredAt":  now_iso,
        }
        producer.send(TOPIC_SESSION, value=session_event)
        session_count += 1

        # ── workout.set.logged (세트별) ──────────────────────────────────────
        # 세션 내 전체 세트 수를 기준으로 occurredAt 을 균등 배분
        total_sets = sum(
            len(ex.get("sets", []))
            for ex in s.get("exercises", [])
        )
        set_interval = duration_sec / max(total_sets, 1)
        elapsed = 0.0

        for ex in s.get("exercises", []):
            exercise_id   = str(ex.get("exerciseId", ""))
            exercise_name = ex.get("exerciseName", "")
            muscle_group  = ex.get("muscleGroup", "")

            for set_rec in ex.get("sets", []):
                set_no   = set_rec.get("setNo", 1)
                reps     = set_rec.get("reps", 10)
                weight   = float(set_rec.get("weightKg", 0.0) or 0.0)
                volume   = round(weight * reps, 2)
                occurred = datetime.fromtimestamp(
                    started_at.timestamp() + elapsed,
                    tz=timezone.utc
                )

                set_event = {
                    "eventType":    "WORKOUT_SET_LOGGED",
                    "sessionId":    sid,
                    "userId":       s["userId"],
                    "exerciseId":   exercise_id,
                    "exerciseName": exercise_name,
                    "muscleGroup":  muscle_group,
                    "setNo":        set_no,
                    "reps":         reps,
                    "weightKg":     weight,
                    "volume":       volume,
                    "isSuccess":    set_rec.get("isSuccess", True),
                    "occurredAt":   _iso(occurred),
                }
                producer.send(TOPIC_SET, value=set_event)
                set_count += 1
                elapsed += set_interval

        print(f"  ✓ {started_at.strftime('%Y-%m-%d')} {s.get('planName','')} "
              f"— session + {total_sets}세트 이벤트")

    producer.flush()
    print(f"\n  총 session.completed={session_count}건, set.logged={set_count}건 발행")


# ──────────────────────────────────────────────────────────────────────────────
# 3단계: ES 갱신 대기 및 검증
# ──────────────────────────────────────────────────────────────────────────────

def verify():
    import urllib.request, urllib.error
    print("\n▶ 3단계: 검증 (10초 대기 후)")
    time.sleep(10)

    try:
        with urllib.request.urlopen("http://localhost:9200/gymplan-sessions-*/_search?"
                                    "size=0&track_total_hits=true",
                                    timeout=5) as r:
            d = json.loads(r.read())
            total = d["hits"]["total"]["value"]
            print(f"  ES gymplan-sessions-* 문서 수: {total}")
    except Exception as e:
        print(f"  ES 조회 실패: {e}")

    # Analytics summary
    try:
        with urllib.request.urlopen("http://localhost:9200/gymplan-sessions-*/_search",
                                    data=json.dumps({
                                        "size": 0,
                                        "aggs": {
                                            "avg_dur": {"avg": {"field": "durationSec"}},
                                            "total_sessions": {"value_count": {"field": "sessionId.keyword"}}
                                        }
                                    }).encode(),
                                    timeout=5) as r:
            d = json.loads(r.read())
            aggs = d.get("aggregations", {})
            avg_dur = aggs.get("avg_dur", {}).get("value", 0) or 0
            total_s = aggs.get("total_sessions", {}).get("value", 0)
            print(f"  평균 durationSec: {avg_dur:.0f}초 ({avg_dur/60:.1f}분)")
            print(f"  총 세션 수: {total_s}")
    except Exception as e:
        print(f"  집계 조회 실패: {e}")


# ──────────────────────────────────────────────────────────────────────────────
# 메인
# ──────────────────────────────────────────────────────────────────────────────

def main():
    print("=" * 60)
    print("  GymPlan 더미 데이터 duration / ES 수정")
    print("=" * 60)

    # MongoDB 연결
    try:
        mongo = MongoClient(MONGO_URI, serverSelectionTimeoutMS=3000)
        mongo.admin.command("ping")
        db = mongo["gymplan_workout"]
        print("✓ MongoDB 연결 성공")
    except Exception as e:
        print(f"✗ MongoDB 연결 실패: {e}")
        sys.exit(1)

    # Kafka 프로듀서 연결
    try:
        producer = KafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP,
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
            request_timeout_ms=10000,
            max_block_ms=10000,
        )
        print(f"✓ Kafka 연결 성공 ({KAFKA_BOOTSTRAP})")
    except NoBrokersAvailable:
        print(f"✗ Kafka 브로커 연결 실패 ({KAFKA_BOOTSTRAP})")
        sys.exit(1)

    try:
        sessions = fix_mongo_duration(db)
        replay_kafka_events(producer, sessions)
        verify()

        print("\n" + "=" * 60)
        print("  ✅ 수정 완료")
        print(f"  세션 {len(sessions)}개 durationSec 보정")
        print("  Kafka 이벤트 재발행 → analytics-service ES upsert 진행 중")
        print("=" * 60)
    finally:
        producer.close()
        mongo.close()


if __name__ == "__main__":
    main()
