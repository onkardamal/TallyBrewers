-- V3__add_session_ip_and_user_agent.sql
-- Add ip_address and user_agent columns to sessions table

ALTER TABLE sessions ADD COLUMN ip_address VARCHAR(45);
ALTER TABLE sessions ADD COLUMN user_agent VARCHAR(255);
