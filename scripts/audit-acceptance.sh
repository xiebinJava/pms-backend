#!/usr/bin/env bash
set -euo pipefail

: "${PMS_QA_ADMIN_EMAIL:?set PMS_QA_ADMIN_EMAIL to a local QA administrator email}"
: "${PMS_QA_MEMBER_EMAIL:?set PMS_QA_MEMBER_EMAIL to a local QA member email}"
: "${PMS_QA_PASSWORD:?set PMS_QA_PASSWORD to a local QA password}"

export PMS_AUDIT_API_BASE="${PMS_API_BASE:-http://127.0.0.1:8080/api}"

node --input-type=module <<'NODE'
import assert from 'node:assert/strict'

const base = process.env.PMS_AUDIT_API_BASE

async function login(email) {
  const response = await fetch(`${base}/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-request-id': 'audit-acceptance-login' },
    body: JSON.stringify({ email, password: process.env.PMS_QA_PASSWORD }),
  })
  const body = await response.json()
  assert.equal(response.status, 200, `login failed for ${email}`)
  return body.data.token ?? body.data.accessToken
}

async function request(path, token, method = 'GET', payload, requestId = 'audit-acceptance-read') {
  const headers = { 'x-request-id': requestId }
  if (token) headers.authorization = `Bearer ${token}`
  if (payload !== undefined) headers['content-type'] = 'application/json'
  return fetch(`${base}${path}`, {
    method,
    headers,
    body: payload === undefined ? undefined : JSON.stringify(payload),
  })
}

const adminToken = await login(process.env.PMS_QA_ADMIN_EMAIL)
const memberToken = await login(process.env.PMS_QA_MEMBER_EMAIL)

assert.equal((await request('/admin/audit')).status, 401)
assert.equal((await request('/admin/audit', memberToken)).status, 403)

const created = await request('/projects', adminToken, 'POST', {
  name: `audit-acceptance-${Date.now()}`,
  status: 1,
  priority: 1,
  orgUnitId: 8,
}, 'audit-acceptance-write')
assert.equal(created.status, 200)
const projectId = (await created.json()).data.id

try {
  const page = await request(`/admin/audit?projectId=${projectId}&result=SUCCESS&currPage=1&pageSize=5`, adminToken)
  assert.equal(page.status, 200)
  const pageBody = await page.json()
  assert.ok(pageBody.data?.list?.length >= 1)
  assert.ok(pageBody.data.list.every(row => row.result === 'SUCCESS' && row.projectId === projectId))

  const noMatch = await request('/admin/audit?requestId=audit-acceptance-no-match&currPage=1&pageSize=5', adminToken)
  assert.equal(noMatch.status, 200)
  assert.equal((await noMatch.json()).data.total, 0)

  const detail = await request(`/admin/audit/${pageBody.data.list[0].id}`, adminToken)
  assert.equal(detail.status, 200)
  assert.equal((await detail.json()).data.projectId, projectId)

  console.log(`Audit API acceptance passed: ${pageBody.data.list.length} records inspected`)
} finally {
  const deleted = await request(`/projects/${projectId}`, adminToken, 'DELETE', undefined, 'audit-acceptance-write')
  assert.equal(deleted.status, 200)
}
NODE
