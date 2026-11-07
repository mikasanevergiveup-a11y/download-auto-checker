import { db } from 'sdk';
import { EndpointError } from 'sdk';
import { api } from 'sdk';
import { eq } from 'sdk/db';
import { users, androidSessions, androidClaims } from '../schema.js';
import { getSetting, updateUserBalance } from '../lib/db.js';

const REQUIRED = 5;

function dayStart() {
  const n = Math.floor(Date.now() / 1000);
  return n - (n % 86400);
}

export default async function (input, ctx) {
  const auth = String(input?.sessionToken || input?.token || '').trim();
  if (!auth) throw new EndpointError('Unauthorized', { code: 'NO_TOKEN' });

  const sessions = await db.select().from(androidSessions).where(eq(androidSessions.token, auth)).all();
  const session = sessions[0];
  if (!session) throw new EndpointError('Invalid session', { code: 'SESSION' });

  const taskType = parseInt(input?.taskType, 10);
  if (![1, 2].includes(taskType)) throw new EndpointError('taskType must be 1 or 2', { code: 'TASK' });

  const installs = Array.isArray(input?.installs) ? input.installs : [];
  const packages = [...new Set(installs.map((x) => String(x?.packageName || '').trim()).filter(Boolean))];
  if (packages.length < REQUIRED) {
    throw new EndpointError(`Need ${REQUIRED} unique installs, got ${packages.length}`, { code: 'COUNT' });
  }

  const idem = String(input?.idempotencyKey || '').trim() ||
    `${session.telegramId}-t${taskType}-${dayStart()}`;

  const existing = await db.select().from(androidClaims).where(eq(androidClaims.idempotencyKey, idem)).all();
  if (existing.length) {
    const u = await db.select().from(users).where(eq(users.telegramId, session.telegramId)).get();
    return {
      ok: true,
      alreadyClaimed: true,
      stage: taskType === 1 ? 'TASK2' : 'COMPLETED',
      balances: { coin: u?.coin || 0, dcoin: u?.dcoin || 0, mmk: u?.mmk || 0 },
    };
  }

  const user = await db.select().from(users).where(eq(users.telegramId, session.telegramId)).get();
  if (!user) throw new EndpointError('User missing', { code: 'NO_USER' });
  if (user.banned) throw new EndpointError('Banned', { code: 'BANNED' });

  const now = Math.floor(Date.now() / 1000);
  if ((user.premiumUntil || 0) <= now) {
    throw new EndpointError('Premium expired', { code: 'NO_PREMIUM' });
  }

  // Task order
  const start = dayStart();
  const claims = await db.select().from(androidClaims).where(eq(androidClaims.telegramId, user.telegramId)).all();
  const task1Done = claims.some((c) => c.taskType === 1);
  if (taskType === 2 && !task1Done) {
    throw new EndpointError('Complete Task 1 first', { code: 'ORDER' });
  }
  if (taskType === 1 && claims.some((c) => c.taskType === 1 && (c.idempotencyKey || '').includes(String(start)))) {
    // still allow if different day key; unique constraint handles exact dup
  }

  const rewardKey = taskType === 2 ? 'reward_task2' : 'reward_task1';
  let amount = parseInt(await getSetting(rewardKey) || (await getSetting('reward_coin')) || '100', 10);
  const rType = parseInt(await getSetting('reward_type') || '1', 10);
  // Premium bonus only for Task 1 (+50%)
  if (taskType === 1) amount = Math.floor(amount * 1.5);

  if (rType === 2) await updateUserBalance(user.telegramId, { mmkDelta: amount });
  else if (rType === 3) await updateUserBalance(user.telegramId, { dcoinDelta: amount });
  else await updateUserBalance(user.telegramId, { coinDelta: amount });

  await db.insert(androidClaims).values({
    telegramId: user.telegramId,
    taskType,
    idempotencyKey: idem,
    packagesJson: JSON.stringify(packages.slice(0, REQUIRED)),
    rewardAmount: amount,
    rewardType: rType,
  }).run();

  await db.update(androidSessions).set({ lastSeenAt: now }).where(eq(androidSessions.token, auth)).run();

  const updated = await db.select().from(users).where(eq(users.telegramId, user.telegramId)).get();
  const unit = rType === 2 ? 'MMK' : rType === 3 ? 'Dcoin' : 'Coin';

  try {
    await api.sendMessage({
      chat_id: user.telegramId,
      text:
        `✅ <b>Android Auto Checker</b>\n` +
        `Task ${taskType} ပြီးပါပြီ!\n` +
        `🎁 Reward: +${amount} ${unit}\n` +
        (taskType === 1 ? `➡️ Task 2 သို့ အလိုအလျောက် ဆက်လုပ်ပါ (နောက် App ၅ ခု)` : `🎉 ဒီနေ့ tasks ပြီးပါပြီ`),
      parse_mode: 'HTML',
    });
  } catch (e) {}

  return {
    ok: true,
    taskType,
    rewarded: amount,
    rewardType: rType,
    stage: taskType === 1 ? 'TASK2' : 'COMPLETED',
    balances: {
      coin: updated?.coin || 0,
      dcoin: updated?.dcoin || 0,
      mmk: updated?.mmk || 0,
    },
  };
}
