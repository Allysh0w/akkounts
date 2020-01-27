CREATE TABLE IF NOT EXISTS balance (
    account_id text,
    offset timeuuid,
    seq_no bigint,
    balance bigint,
    PRIMARY KEY (account_id)
);
