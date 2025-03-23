import json
import time
import random
from datetime import datetime, timedelta
from confluent_kafka import SerializingProducer
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroSerializer
from confluent_kafka.serialization import StringSerializer, SerializationContext, MessageField
from confluent_kafka.schema_registry import Schema

def generate_click_event(start_date="2024-01-01", end_date="2025-02-02"):
    category_type = random.choice(["S", "0", "category", "brand"])
    start = datetime.strptime(start_date, "%Y-%m-%d")
    end = datetime.strptime(end_date, "%Y-%m-%d")
    category = "S" if category_type == "S" else "0" if category_type == "0" else random.randint(1, 50) if category_type == "category" else random.randint(100000000, 999999999)
    random_time = start + timedelta(seconds=random.uniform(0, (end - start).total_seconds()))
    return {
        'event': 'click',
        'sessionId': str(random.randint(0, 1000)),
        'timestamp': random_time.isoformat() + "Z",
        'category': str(category),
        'itemId': str(random.randint(100000, 999999))
    }

def generate_buy_event(start_date="2024-01-01", end_date="2025-02-02"):
    start = datetime.strptime(start_date, "%Y-%m-%d")
    end = datetime.strptime(end_date, "%Y-%m-%d")
    random_time = start + timedelta(seconds=random.uniform(0, (end - start).total_seconds()))
    return {
        'event': 'buy',
        'sessionId': str(random.randint(0, 1000)),
        'timestamp': random_time.isoformat() + "Z",
        'itemId': str(random.randint(100000, 999999)),
        'price': random.randint(1, 2000),
        'quantity': random.randint(1, 30)
    }

def load_avro_schema(file_path):
    try:
        with open(file_path, "r") as f:
            schema = json.load(f)
            if not schema:
                raise ValueError(f"Schema file {file_path} is empty!")
            return json.dumps(schema)  # Convert dict to string
    except (FileNotFoundError, json.JSONDecodeError, ValueError) as e:
        print(f"Error: {e}")
        exit(1)

def delivery_report(err, msg):
    if err is not None:
        print(f"Delivery failed: {err}")
    else:
        print(f"Message sent to {msg.topic()} [{msg.partition()}] at offset {msg.offset()}")

def register_schema_if_needed(schema_registry_client, subject_name, schema_str):
    """
    Kiểm tra nếu schema đã được đăng ký, nếu chưa thì đăng ký mới.
    """
    try:
        # Lấy danh sách schema đã đăng ký
        subjects = schema_registry_client.get_subjects()
        if subject_name in subjects:
            print(f"Schema '{subject_name}' đã được đăng ký trước đó. Không cần đăng ký lại.")
            return schema_registry_client.get_latest_version(subject_name).version  # Lấy ID phiên bản mới nhất

        # Nếu chưa đăng ký, tiến hành đăng ký
        schema = Schema(schema_str, "AVRO")
        schema_id = schema_registry_client.register_schema(subject_name, schema)
        print(f"Schema '{subject_name}' đã được đăng ký với ID: {schema_id}")
        return schema_id

    except Exception as e:
        print(f"Lỗi khi đăng ký schema {subject_name}: {e}")
        return None

def main():
    topics = ['click_events', 'buy_events']
    schema_registry_url = 'http://localhost:8082'
    schema_registry_client = SchemaRegistryClient({'url': schema_registry_url})
    avro_schemas_path = "../avro_schema"
    buy_schema_str = load_avro_schema(f"{avro_schemas_path}/buy_events.avsc")
    click_schema_str = load_avro_schema(f"{avro_schemas_path}/click_events.avsc")

    register_schema_if_needed(schema_registry_client, "test_buy_events", buy_schema_str)
    register_schema_if_needed(schema_registry_client, "test_click_events", click_schema_str)

    avro_serializer_buy = AvroSerializer(schema_registry_client, buy_schema_str)
    avro_serializer_click = AvroSerializer(schema_registry_client, click_schema_str)

    producer_config = {
        "bootstrap.servers": "localhost:9092",
        "linger.ms": 100,
        "batch.size": 16384,
        "key.serializer": StringSerializer(),
        "value.serializer": None
    }

    producer = SerializingProducer(producer_config)
    cur_time = datetime.now()

    while (datetime.now() - cur_time).seconds < 10000:
        topic = random.choice(topics)
        avro_serializer = avro_serializer_buy if topic == "buy_events" else avro_serializer_click
        data = generate_buy_event() if topic == "buy_events" else generate_click_event()
        avro_data = avro_serializer(data, SerializationContext(topic, MessageField.VALUE))

        try:
            print(f"Producing Avro: {data}")
            producer.produce(topic=topic, value=avro_data, on_delivery=delivery_report)
            producer.poll(0)
            time.sleep(2)
        except BufferError:
            print(f"Local producer queue is full ({len(producer)} messages awaiting delivery): retrying...")
            time.sleep(1)
        except Exception as e:
            print(f"Error: {e}")

    producer.flush()

if __name__ == "__main__":
    main()