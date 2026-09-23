import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
import base64
import uuid
import time

from app.main import app
from app.database import Base, get_db

SQLALCHEMY_DATABASE_URL = "sqlite:///./test.db"
engine = create_engine(SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False})
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base.metadata.create_all(bind=engine)

def override_get_db():
    try:
        db = TestingSessionLocal()
        yield db
    finally:
        db.close()

app.dependency_overrides[get_db] = override_get_db

client = TestClient(app)

class Device:
    def __init__(self, name):
        self.name = name
        self.device_id = str(uuid.uuid4())
        self.private_key = ec.generate_private_key(ec.SECP256R1())
        self.public_key = self.private_key.public_key()
        self.pub_pem = self.public_key.public_bytes(
            encoding=Encoding.PEM,
            format=PublicFormat.SubjectPublicKeyInfo
        ).decode('utf-8')
        
    def sign_tx(self, receiver_id, amount, sequence_counter):
        tx_id = str(uuid.uuid4())
        nonce = str(uuid.uuid4())
        timestamp = int(time.time() * 1000)
        
        payload = f"{tx_id}|{self.device_id}|{receiver_id}|{amount}|{sequence_counter}|{nonce}|{timestamp}"
        
        sig = self.private_key.sign(
            payload.encode('utf-8'),
            ec.ECDSA(hashes.SHA256())
        )
        
        return {
            "tx_id": tx_id,
            "sender_id": self.device_id,
            "receiver_id": receiver_id,
            "amount": amount,
            "sequence_counter": sequence_counter,
            "nonce": nonce,
            "timestamp": timestamp,
            "signature": base64.b64encode(sig).decode('utf-8')
        }

def test_double_spend_reconciliation():
    # Setup
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    
    alice = Device("Alice")
    bob = Device("Bob")
    charlie = Device("Charlie")
    
    # Register Alice, Bob, and Charlie
    for d in [alice, bob, charlie]:
        res = client.post("/register", json={"device_id": d.device_id, "public_key_pem": d.pub_pem})
        assert res.status_code == 200
        assert res.json()["balance"] == 1000.0

    # Alice creates Tx1 (Counter=1) paying Bob ₹100
    tx1 = alice.sign_tx(bob.device_id, 100.0, 1)
    
    # Attack: Alice creates Tx2 (Counter=1) paying Charlie ₹100 (simulating rollback)
    tx2 = alice.sign_tx(charlie.device_id, 100.0, 1)
    
    # Bob syncs Tx1
    res_bob = client.post("/sync", json={"transactions": [tx1]})
    assert res_bob.status_code == 200
    bob_report = res_bob.json()["report"][0]
    assert bob_report["tx_id"] == tx1["tx_id"]
    assert bob_report["status"] == "COMMITTED"
    
    # Charlie syncs Tx2
    res_charlie = client.post("/sync", json={"transactions": [tx2]})
    assert res_charlie.status_code == 200
    charlie_report = res_charlie.json()["report"][0]
    assert charlie_report["tx_id"] == tx2["tx_id"]
    assert charlie_report["status"] == "REJECTED"
    assert charlie_report["message"] == "REJECTED_DOUBLE_SPEND"

