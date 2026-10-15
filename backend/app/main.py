from fastapi import FastAPI, Depends, HTTPException
from fastapi.responses import HTMLResponse
from sqlalchemy.orm import Session
from . import models, schemas, engine, database

models.Base.metadata.create_all(bind=database.engine)

app = FastAPI(title="DTOPS Backend")

def get_user_highest_seq(user_id: str, db: Session) -> int:
    highest_tx = db.query(models.Transaction).filter(
        models.Transaction.sender_id == user_id,
        models.Transaction.status == models.TransactionStatus.COMMITTED
    ).order_by(models.Transaction.sequence_counter.desc()).first()
    return highest_tx.sequence_counter if highest_tx else 0

@app.get("/health")
def health_check():
    return {"status": "ok", "service": "DTOPS Settlement Engine"}

@app.post("/register", response_model=schemas.RegisterResponse)
def register(request: schemas.RegisterRequest, db: Session = Depends(database.get_db)):
    existing = db.query(models.User).filter(models.User.id == request.device_id).first()
    if existing:
        highest_seq = get_user_highest_seq(existing.id, db)
        return schemas.RegisterResponse(status="Already registered", balance=existing.online_balance, highest_sequence_counter=highest_seq)
        
    new_user = models.User(
        id=request.device_id,
        public_key_pem=request.public_key_pem,
        online_balance=1000.0  # Initialize with ₹1000
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    return schemas.RegisterResponse(status="Success", balance=new_user.online_balance, highest_sequence_counter=0)

@app.post("/topup")
def topup_user(req: schemas.TopUpRequest, db: Session = Depends(database.get_db)):
    user = db.query(models.User).filter(models.User.id == req.user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    if req.amount <= 0:
        raise HTTPException(status_code=400, detail="Invalid top-up amount")
    
    user.online_balance += req.amount
    db.commit()
    return {"message": "Top-up successful", "new_balance": user.online_balance}

@app.get("/balance/{user_id}", response_model=schemas.BalanceResponse)
def get_balance(user_id: str, db: Session = Depends(database.get_db)):
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return schemas.BalanceResponse(balance=user.online_balance)

@app.get("/user/{user_id}/state", response_model=schemas.UserStateResponse)
def get_user_state(user_id: str, db: Session = Depends(database.get_db)):
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    highest_seq = get_user_highest_seq(user.id, db)
    return schemas.UserStateResponse(user_id=user.id, balance=user.online_balance, highest_sequence_counter=highest_seq)

@app.post("/sync", response_model=schemas.SyncResponse)
def sync_transactions(request: schemas.SyncRequest, db: Session = Depends(database.get_db)):
    report = []
    # Sort transactions by sequence_counter to process in order
    transactions = sorted(request.transactions, key=lambda x: x.sequence_counter)
    
    for tx_in in transactions:
        # Check if already processed
        existing = db.query(models.Transaction).filter(models.Transaction.tx_id == tx_in.tx_id).first()
        if existing:
            report.append(schemas.SyncReportItem(tx_id=tx_in.tx_id, status=existing.status.value, message="Already processed"))
            continue

        is_valid, msg = engine.verify_offline_transaction(tx_in, db)
        
        status = models.TransactionStatus.COMMITTED if is_valid else models.TransactionStatus.REJECTED
        
        new_tx = models.Transaction(
            tx_id=tx_in.tx_id,
            sender_id=tx_in.sender_id,
            receiver_id=tx_in.receiver_id,
            amount=tx_in.amount,
            sequence_counter=tx_in.sequence_counter,
            nonce=tx_in.nonce,
            timestamp=tx_in.timestamp,
            signature=tx_in.signature,
            status=status
        )
        db.add(new_tx)
        
        if is_valid:
            sender = db.query(models.User).filter(models.User.id == tx_in.sender_id).first()
            receiver = db.query(models.User).filter(models.User.id == tx_in.receiver_id).first()
            
            if sender:
                sender.online_balance -= tx_in.amount
            if receiver:
                receiver.online_balance += tx_in.amount
                
        db.commit()
        report.append(schemas.SyncReportItem(tx_id=tx_in.tx_id, status=status.value, message=msg))
        
    return schemas.SyncResponse(report=report)

@app.get("/users")
def get_all_users(db: Session = Depends(database.get_db)):
    """View all registered users and their current global balances."""
    return db.query(models.User).all()

@app.get("/transactions")
def get_all_transactions(db: Session = Depends(database.get_db)):
    """View the entire global ledger of synced offline transactions."""
    return db.query(models.Transaction).order_by(models.Transaction.timestamp.desc()).all()

@app.get("/conflicts")
def get_conflict_logs(db: Session = Depends(database.get_db)):
    """View the audit log of all detected double-spends and rollback attacks."""
    return db.query(models.ConflictLog).all()

@app.get("/dashboard", response_class=HTMLResponse)
def admin_dashboard(db: Session = Depends(database.get_db)):
    users = db.query(models.User).all()
    transactions = db.query(models.Transaction).order_by(models.Transaction.timestamp.desc()).all()
    conflicts = db.query(models.ConflictLog).all()
    
    html_content = f"""
    <html>
        <head>
            <title>DTOPS Backend Dashboard</title>
            <meta http-equiv="refresh" content="5"> <!-- Auto refresh every 5 seconds -->
            <style>
                body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background-color: #f1f5f9; padding: 20px; color: #1e293b; }}
                h1 {{ color: #0f172a; text-align: center; }}
                .container {{ display: flex; flex-direction: column; gap: 20px; max-width: 1200px; margin: 0 auto; }}
                .card {{ background: white; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1); }}
                table {{ width: 100%; border-collapse: collapse; margin-top: 10px; }}
                th, td {{ padding: 12px; text-align: left; border-bottom: 1px solid #e2e8f0; }}
                th {{ background-color: #f8fafc; color: #475569; font-weight: 600; }}
                .status-COMMITTED {{ color: #10b981; font-weight: bold; }}
                .status-REJECTED {{ color: #ef4444; font-weight: bold; }}
                .status-PENDING {{ color: #f59e0b; font-weight: bold; }}
            </style>
        </head>
        <body>
            <h1>🌐 DTOPS Settlement Server</h1>
            <p style="text-align: center; color: #64748b;">Live updating every 5 seconds...</p>
            <div class="container">
                
                <div class="card">
                    <h2>👥 Registered Users (Global Balance)</h2>
                    <table>
                        <tr><th>User ID</th><th>Online Balance</th></tr>
                        {"".join(f"<tr><td>{u.id}</td><td>₹{u.online_balance}</td></tr>" for u in users)}
                    </table>
                </div>

                <div class="card">
                    <h2>💸 Global Transaction Ledger</h2>
                    <table>
                        <tr><th>Tx ID</th><th>From</th><th>To</th><th>Amount</th><th>Seq Counter</th><th>Status</th></tr>
                        {"".join(f"<tr><td style='font-size: 12px;'>{t.tx_id}</td><td>{t.sender_id}</td><td>{t.receiver_id}</td><td>₹{t.amount}</td><td>{t.sequence_counter}</td><td class='status-{t.status.value}'>{t.status.value}</td></tr>" for t in transactions)}
                    </table>
                </div>
                
                <div class="card">
                    <h2>🚨 Security & Conflict Logs (Double Spends)</h2>
                    <table>
                        <tr><th>Log ID</th><th>Attacker ID</th><th>Attempted Seq</th><th>Action Taken</th></tr>
                        {"".join(f"<tr><td>{c.id}</td><td style='color: #ef4444; font-weight: bold;'>{c.offending_sender_id}</td><td>{c.duplicate_sequence}</td><td>{c.resolved_action}</td></tr>" for c in conflicts)}
                    </table>
                </div>
                
            </div>
        </body>
    </html>
    """
    return HTMLResponse(content=html_content)

