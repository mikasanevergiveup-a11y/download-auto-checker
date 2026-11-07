import { db } from 'sdk';
import { EndpointError } from 'sdk';
import { eq, and } from 'sdk/db';
import { users, androidCodes, androidSessions, androidClaims } from '../schema.js';
import { findUserByUsernameOrId, getSetting } from '../lib/db.js';
import crypto from 'crypto';

function token() {
  return crypto.randomBytes(32).toString('hex');
}

function dayStart() {
  const n = Math.floor(Date.now() / 1000);
  return n - (n % 86400);
}

export default async function (input) {
  const raw = String(input?.usernameOrId || '').trim();
  const code = String(input?.code || '').trim();
  const deviceId = String(input?.deviceId || '').trim() || 'unknown';
  if (!raw || !code) throw new EndpointError('usernameOrId + code required', { code: 'ARGS' });

  const user = await findUserByUsernameOrId(raw);
  if (!user) throw new EndpointError('User not found', { code: 'NO_USER' });
  if (user.banned) throw new EndpointError('Banned', { code: 'BANNED' });

  const now = Math.floor(Date.now() / 1000);
  if ((user.premiumUntil || 0) <= now) {
    throw new EndpointError('Premium Pass required', { code: 'NO_PREMIUM' });
  }

  const rows = await db.select().from(androidCodes)
    .where(and(eq(androidCodes.telegramId, user.telegramId), eq(androidCodes.code, code)))
    .all();
  const row = rows.find((r) => !r.used && (r.expiresAt || 0) >= now);
  if (!row) throw new EndpointError('Invalid or expired code', { code: 'BAD_CODE' });

  await db.update(androidCodes).set({ used: true }).where(eq(androidCodes.id, row.id)).run();

  const sessionToken = token();
  await db.insert(androidSessions).values({
    telegramId: user.telegramId,
    token: sessionToken,
    deviceId,
    lastSeenAt: now,
  }).run();

  const start = dayStart();
  const claims = await db.select().from(androidClaims).where(eq(androidClaims.telegramId, user.telegramId)).all();
  const task1Done = claims.some((c) => c.taskType === 1 && (typeof c.createdAt === 'number' ? c.createdAt : 0) >= start);
  const task2Done = claims.some((c) => c.taskType === 2 && (typeof c.createdAt === 'number' ? c.createdAt : 0) >= start);

  let stage = 'TASK1';
  if (task1Done && !task2Done) stage = 'TASK2';
  if (task1Done && task2Done) stage = 'COMPLETED';

  const rewardTask1 = parseInt(await getSetting('reward_task1') || (await getSetting('reward_coin')) || '100', 10);
  const rewardTask2 = parseInt(await getSetting('reward_task2') || '150', 10);
  const rewardType = parseInt(await getSetting('reward_type') || '1', 10);

  return {
    ok: true,
    sessionToken,
    user: {
      telegramId: user.telegramId,
      username: user.username || '',
      name: [user.firstName, user.lastName].filter(Boolean).join(' ') || 'User',
      coin: user.coin || 0,
      dcoin: user.dcoin || 0,
      mmk: user.mmk || 0,
      premiumUntil: user.premiumUntil || 0,
      premiumActive: true,
    },
    stage,
    task1Done,
    task2Done,
    requiredInstalls: 5,
    rewardTask1,
    rewardTask2,
    rewardType,
  };
}
