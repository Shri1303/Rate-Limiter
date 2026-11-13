-- Sliding Window Counter rate limiter.
-- Approximates a true sliding log by weighting the previous fixed window's count
-- by how much of it still overlaps the sliding window. Two counters per key
-- (O(1) memory) vs. one ZSET entry per request for a sliding log.
--
-- KEYS[1] = base key (e.g. "rl:ip:1.2.3.4")
-- ARGV[1] = limit
-- ARGV[2] = window size in milliseconds
-- ARGV[3] = current time in milliseconds (passed from the client so the script is deterministic
--           and safe for Redis replication)
--
-- Returns { allowed (1/0), remaining, retryAfterMs }

local key        = KEYS[1]
local limit      = tonumber(ARGV[1])
local window_ms  = tonumber(ARGV[2])
local now_ms     = tonumber(ARGV[3])

local curr_window  = math.floor(now_ms / window_ms)
local prev_window  = curr_window - 1
local curr_key     = key .. ":" .. curr_window
local prev_key     = key .. ":" .. prev_window

local curr_count = tonumber(redis.call("GET", curr_key) or "0")
local prev_count = tonumber(redis.call("GET", prev_key) or "0")

-- Fraction of the previous window that still falls inside the sliding window.
local elapsed_in_curr = now_ms - (curr_window * window_ms)
local prev_weight     = (window_ms - elapsed_in_curr) / window_ms

local estimated = math.floor(prev_count * prev_weight + curr_count)

if estimated >= limit then
  -- Time until enough of the previous window has aged out, or until the next window starts.
  local retry_after = window_ms - elapsed_in_curr
  return { 0, 0, retry_after }
end

-- Admit the request atomically. Everything in this script runs as a single Redis command,
-- so concurrent callers cannot both observe "estimated < limit" and both increment past it.
local new_count = redis.call("INCR", curr_key)
if new_count == 1 then
  -- Keep two windows' worth so the previous-window read above always has data.
  redis.call("PEXPIRE", curr_key, window_ms * 2)
end

local remaining = limit - (estimated + 1)
if remaining < 0 then remaining = 0 end
return { 1, remaining, 0 }
