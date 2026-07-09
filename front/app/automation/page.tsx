'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { BiCheckShield, BiListPlus, BiPause, BiPlay, BiRefresh, BiStop, BiTask, BiTime } from 'react-icons/bi'
import PageHeader from '@/app/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'

interface AutomationTask {
  id: number
  name: string
  platforms: string
  mode: string
  status: string
  keywords?: string
  city?: string
  maxApplications?: number
  maxDailyApplications?: number
  allowRealActions?: number
  reviewApproved?: number
  lastMessage?: string
  createdAt?: string
  updatedAt?: string
}

interface AutomationAudit {
  id: number
  taskId: number
  platform: string
  eventType: string
  result: string
  message: string
  createdAt?: string
}

interface TaskForm {
  name: string
  platforms: string[]
  mode: string
  keywords: string
  city: string
  maxApplications: number
  maxDailyApplications: number
  allowRealActions: boolean
}

const baseUrl = process.env.API_BASE_URL || 'http://localhost:8888'
const platformOptions = ['boss', 'liepin', '51job', 'zhilian']
const autoApplyConfirmationPhrase = 'AUTO_APPLY_ONE_JOB'

const defaultForm: TaskForm = {
  name: 'AI Agent 岗位自动化任务',
  platforms: ['boss'],
  mode: 'DRY_RUN',
  keywords: 'AI Agent, RAG, FastAPI, SSE',
  city: '杭州,上海,苏州,宁波,合肥',
  maxApplications: 1,
  maxDailyApplications: 1,
  allowRealActions: false,
}

export default function AutomationPage() {
  const [form, setForm] = useState<TaskForm>(defaultForm)
  const [tasks, setTasks] = useState<AutomationTask[]>([])
  const [audits, setAudits] = useState<AutomationAudit[]>([])
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null)
  const [confirmationPhrase, setConfirmationPhrase] = useState('')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')

  const selectedTask = useMemo(
    () => tasks.find((task) => task.id === selectedTaskId) || tasks[0],
    [selectedTaskId, tasks]
  )

  const setPatch = (patch: Partial<TaskForm>) => setForm((prev) => ({ ...prev, ...patch }))

  const loadTasks = useCallback(async () => {
    setLoading(true)
    try {
      const response = await fetch(`${baseUrl}/api/automation/tasks`)
      const data = await response.json()
      setTasks(Array.isArray(data) ? data : [])
      if (!selectedTaskId && Array.isArray(data) && data[0]?.id) {
        setSelectedTaskId(data[0].id)
      }
      setMessage('')
    } catch {
      setMessage('自动化任务加载失败')
    } finally {
      setLoading(false)
    }
  }, [selectedTaskId])

  const loadAudit = useCallback(async (taskId?: number | null) => {
    const id = taskId || selectedTask?.id
    if (!id) {
      setAudits([])
      return
    }
    try {
      const response = await fetch(`${baseUrl}/api/automation/tasks/${id}/audit?limit=80`)
      const data = await response.json()
      setAudits(Array.isArray(data) ? data : [])
    } catch {
      setMessage('审计日志加载失败')
    }
  }, [selectedTask?.id])

  useEffect(() => {
    loadTasks()
  }, [loadTasks])

  useEffect(() => {
    loadAudit(selectedTaskId)
  }, [loadAudit, selectedTaskId])

  const createTask = async () => {
    setLoading(true)
    try {
      const response = await fetch(`${baseUrl}/api/automation/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      })
      const data = await response.json()
      if (!data.success) throw new Error(data.message || 'create failed')
      setMessage('任务已创建')
      setSelectedTaskId(data.data.id)
      await loadTasks()
      await loadAudit(data.data.id)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '任务创建失败')
    } finally {
      setLoading(false)
    }
  }

  const runAction = async (taskId: number, action: 'start' | 'approve' | 'pause' | 'resume' | 'stop') => {
    setLoading(true)
    try {
      const response = await fetch(`${baseUrl}/api/automation/tasks/${taskId}/${action}`, requestOptions(action, confirmationPhrase))
      const data = await response.json()
      if (!data.success) throw new Error(data.message || action)
      setMessage(data.data?.lastMessage || '操作完成')
      await loadTasks()
      await loadAudit(taskId)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '操作失败')
    } finally {
      setLoading(false)
    }
  }

  const togglePlatform = (platform: string) => {
    setForm((prev) => {
      const hasPlatform = prev.platforms.includes(platform)
      const nextPlatforms = hasPlatform
        ? prev.platforms.filter((item) => item !== platform)
        : [...prev.platforms, platform]
      return { ...prev, platforms: nextPlatforms.length ? nextPlatforms : ['boss'] }
    })
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-cyan-50 dark:from-blacksection dark:via-blackho dark:to-blacksection">
      <PageHeader
        title="自动化任务"
        subtitle="队列、限额、审计和人工确认"
        icon={<BiTask />}
        actions={
          <div className="flex items-center gap-2">
            <Button onClick={loadTasks} disabled={loading} size="sm" className="rounded-full bg-white/80 text-slate-700 hover:bg-white shadow">
              <BiRefresh className="mr-1" /> 刷新
            </Button>
            <Button onClick={createTask} disabled={loading} size="sm" className="rounded-full bg-gradient-to-r from-emerald-500 to-teal-500 text-white shadow">
              <BiListPlus className="mr-1" /> 新建任务
            </Button>
          </div>
        }
      />

      <div className="max-w-7xl mx-auto p-6 space-y-6">
        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
          <Card className="xl:col-span-1">
            <CardHeader>
              <CardTitle>任务配置</CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              <div className="space-y-2">
                <Label htmlFor="taskName">名称</Label>
                <Input id="taskName" value={form.name} onChange={(event) => setPatch({ name: event.target.value })} />
              </div>

              <div className="space-y-2">
                <Label>平台</Label>
                <div className="grid grid-cols-2 gap-2">
                  {platformOptions.map((platform) => (
                    <button
                      key={platform}
                      type="button"
                      onClick={() => togglePlatform(platform)}
                      className={`rounded-lg border px-3 py-2 text-sm transition ${
                        form.platforms.includes(platform)
                          ? 'border-emerald-400 bg-emerald-500/15 text-emerald-700 dark:text-emerald-200'
                          : 'border-slate-200 bg-white/70 text-slate-600 dark:border-white/10 dark:bg-white/5 dark:text-manatee'
                      }`}
                    >
                      {platform}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="mode">模式</Label>
                <Select id="mode" value={form.mode} onChange={(event) => setPatch({ mode: event.target.value })}>
                  <option value="DRY_RUN">dry-run 只评分</option>
                  <option value="REVIEW_THEN_APPLY">人工确认后投递</option>
                  <option value="AUTO_APPLY">自动投递</option>
                </Select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-2">
                  <Label htmlFor="maxApplications">单任务上限</Label>
                  <Input
                    id="maxApplications"
                    type="number"
                    min={1}
                    max={1}
                    value={form.maxApplications}
                    onChange={() => setPatch({ maxApplications: 1 })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="maxDailyApplications">每日上限</Label>
                  <Input
                    id="maxDailyApplications"
                    type="number"
                    min={1}
                    max={1}
                    value={form.maxDailyApplications}
                    onChange={() => setPatch({ maxDailyApplications: 1 })}
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="city">城市</Label>
                <Input id="city" value={form.city} onChange={(event) => setPatch({ city: event.target.value })} />
              </div>

              <div className="space-y-2">
                <Label htmlFor="keywords">关键词</Label>
                <Textarea id="keywords" rows={4} value={form.keywords} onChange={(event) => setPatch({ keywords: event.target.value })} />
              </div>

              <label className="flex items-center gap-3 rounded-lg border border-amber-300/50 bg-amber-50/70 p-3 text-sm text-amber-800 dark:border-amber-400/30 dark:bg-amber-500/10 dark:text-amber-100">
                <input
                  type="checkbox"
                  checked={form.allowRealActions}
                  onChange={(event) => setPatch({ allowRealActions: event.target.checked })}
                  className="h-4 w-4"
                />
                允许真实平台动作
              </label>

              <div className="space-y-2">
                <Label htmlFor="confirmationPhrase">启动确认短语</Label>
                <Input
                  id="confirmationPhrase"
                  value={confirmationPhrase}
                  onChange={(event) => setConfirmationPhrase(event.target.value)}
                  placeholder={autoApplyConfirmationPhrase}
                />
              </div>
            </CardContent>
          </Card>

          <Card className="xl:col-span-2">
            <CardHeader>
              <CardTitle>任务队列</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {message && (
                <div className="rounded-lg border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-800 dark:border-sky-400/20 dark:bg-sky-500/10 dark:text-sky-100">
                  {message}
                </div>
              )}

              {tasks.length === 0 ? (
                <div className="rounded-lg border border-dashed p-6 text-sm text-muted-foreground">暂无任务</div>
              ) : (
                <div className="space-y-3">
                  {tasks.map((task) => (
                    <div
                      key={task.id}
                      className={`rounded-lg border p-4 transition ${
                        selectedTask?.id === task.id
                          ? 'border-emerald-400 bg-emerald-500/5'
                          : 'border-slate-200 bg-white/70 dark:border-white/10 dark:bg-white/5'
                      }`}
                    >
                      <div className="flex flex-wrap items-start gap-3">
                        <button type="button" className="min-w-0 flex-1 text-left" onClick={() => setSelectedTaskId(task.id)}>
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-semibold">{task.name}</span>
                            <StatusBadge status={task.status} />
                            <span className="rounded-full bg-slate-100 px-2 py-1 text-xs text-slate-600 dark:bg-white/10 dark:text-manatee">{task.mode}</span>
                          </div>
                          <div className="mt-2 text-sm text-muted-foreground">
                            {task.platforms} · 上限 {task.maxApplications || 1}/{task.maxDailyApplications || 1} · {task.lastMessage || '-'}
                          </div>
                        </button>
                        <div className="flex flex-wrap gap-2">
                          <IconButton label="启动" onClick={() => runAction(task.id, 'start')} disabled={loading || startDisabled(task, confirmationPhrase)} icon={<BiPlay />} />
                          <IconButton label="确认" onClick={() => runAction(task.id, 'approve')} disabled={loading} icon={<BiCheckShield />} />
                          <IconButton label="暂停" onClick={() => runAction(task.id, 'pause')} disabled={loading} icon={<BiPause />} />
                          <IconButton label="恢复" onClick={() => runAction(task.id, 'resume')} disabled={loading} icon={<BiTime />} />
                          <IconButton label="停止" onClick={() => runAction(task.id, 'stop')} disabled={loading} icon={<BiStop />} />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>审计日志</CardTitle>
          </CardHeader>
          <CardContent>
            {audits.length === 0 ? (
              <div className="rounded-lg border border-dashed p-6 text-sm text-muted-foreground">暂无审计记录</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[860px] text-sm">
                  <thead>
                    <tr className="border-b text-left text-muted-foreground">
                      <th className="py-2 pr-4 font-medium">时间</th>
                      <th className="py-2 pr-4 font-medium">平台</th>
                      <th className="py-2 pr-4 font-medium">事件</th>
                      <th className="py-2 pr-4 font-medium">结果</th>
                      <th className="py-2 pr-4 font-medium">消息</th>
                    </tr>
                  </thead>
                  <tbody>
                    {audits.map((audit) => (
                      <tr key={audit.id} className="border-b last:border-b-0">
                        <td className="py-3 pr-4 text-muted-foreground">{formatTime(audit.createdAt)}</td>
                        <td className="py-3 pr-4">{audit.platform}</td>
                        <td className="py-3 pr-4">{audit.eventType}</td>
                        <td className="py-3 pr-4">{audit.result}</td>
                        <td className="py-3 pr-4 text-muted-foreground">{audit.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function requestOptions(action: string, confirmationPhrase: string): RequestInit {
  if (action !== 'start') {
    return { method: 'POST' }
  }

  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ confirmationPhrase }),
  }
}

function startDisabled(task: AutomationTask, confirmationPhrase: string) {
  if (task.mode === 'DRY_RUN' || task.allowRealActions !== 1) {
    return false
  }
  return confirmationPhrase.trim() !== autoApplyConfirmationPhrase
}

function StatusBadge({ status }: { status: string }) {
  const color = status === 'COMPLETED'
    ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-200'
    : status === 'RUNNING'
    ? 'bg-sky-100 text-sky-700 dark:bg-sky-500/15 dark:text-sky-200'
    : status === 'BLOCKED' || status === 'FAILED' || status === 'RATE_LIMITED'
    ? 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-200'
    : 'bg-slate-100 text-slate-700 dark:bg-white/10 dark:text-manatee'

  return <span className={`rounded-full px-2 py-1 text-xs ${color}`}>{status}</span>
}

function IconButton({
  label,
  icon,
  disabled,
  onClick,
}: {
  label: string
  icon: React.ReactNode
  disabled?: boolean
  onClick: () => void
}) {
  return (
    <Button
      type="button"
      size="sm"
      disabled={disabled}
      onClick={onClick}
      className="rounded-full bg-white/80 px-3 text-slate-700 shadow hover:bg-white dark:bg-white/10 dark:text-white dark:hover:bg-white/15"
      title={label}
    >
      {icon}
      <span className="sr-only">{label}</span>
    </Button>
  )
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}
