-- Add ai_model column to store the user's preferred LLM
ALTER TABLE user_profiles
ADD COLUMN ai_model VARCHAR(50) DEFAULT 'llama3';
