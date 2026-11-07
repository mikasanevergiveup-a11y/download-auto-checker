import { db } from 'sdk';
import { EndpointError } from 'sdk';
import { api } from 'sdk';
import { eq } from 'sdk/db';
import { users, androidCodes } from '../schema.js';
import { findUserByUsernameOrId } from '../lib/db.js';

function genCode() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

export default async function (input, ctx) {
  // Public endpoint — Telegram WebApp initData not required for Android app
  const raw = String(input?.usernameOrId || '').trim();
  if (!raw) throw new EndpointError('username or id required', { code: 'ARGS' });

  const user = await findUserByUsernameOrId(raw);
  if (!user) throw new EndpointError('User not found. Open the Telegram bot and /start first.', { code: 'NO_USER' });
  if (user.banned) throw new EndpointError('Account banned', { code: 'BANNED' });

  const now = Math.floor(Date.now() / 1000);
  if ((user.premiumUntil || 0) <= now) {
    throw new EndpointError('Premium Pass required', { code: 'NO_PREMIUM' });
  }

  const code = genCode();
  const expiresAt = now + 10 * 60;

  // invalidate old unused codes
  try {
    const old = await db.select().from(androidCodes).where(eq(androidCodes.telegramId, user.telegramId)).all();
    for (const row of old) {
      if (!row.used) {
        await db.update(androidCodes).set({ used: true }).where(eq(androidCodes.id, row.id)).run();
      }
    }
  } catch (e) {}

  await db.insert(androidCodes).values({
    telegramId: user.telegramId,
    code,
    expiresAt,
    used: false,
  }).run();

  const name = [user.firstName, user.lastName].filter(Boolean).join(' ') || 'User';
  try {
    await api.sendMessage({
      chat_id: user.telegramId,
      text:
        `🔐 <b>Android App Verification</b>\n\n` +
        `မင်္ဂလာပါ ${name}\n` +
        `သင့် verification code:\n\n` +
        `<code>${code}</code>\n\n` +
        `⏰ ၁၀ မိနစ်အတွင်း Android app ထဲ ထည့်ပါ။\n` +
        `ဤ code ကို မည်သူနှင့်မျှ မမျှဝေပါနှင့်။`,
      parse_mode: 'HTML',
    });
  } catch (e) {
    throw new EndpointError('Cannot message user. User must /start the bot first.', { code: 'NO_CHAT' });
  }

  return {
    ok: true,
    message: 'Code sent to your Telegram',
    telegramId: user.telegramId,
    expiresInSec: 600,
  };
}
