'use client'

import { useState, useEffect } from 'react'
import { createSSEWithBackoff } from '@/lib/sse'
import { BiLogOut, BiSave, BiBriefcase, BiPlay, BiStop, BiLogIn } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import AnalysisContent from '@/app/zhilian/analysis/AnalysisContent'
import PageHeader from '@/app/components/PageHeader'
import SafetyControls, { getDeliveryModeLabel, withSafetyDefaults } from '@/app/components/SafetyControls'

interface ZhilianConfig {
  id?: number
  keywords?: string
  cityCode?: string
  salary?: string
  dryRun?: boolean
  maxDeliveries?: number
  stopOnCaptcha?: boolean
  stopOnRiskText?: boolean
  allowSimilarJobs?: boolean
  browserProfileMode?: string
}

interface Option { name: string; code: string }
interface ZhilianOptions { city: Option[] }

export default function ZhilianPage() {
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [isDelivering, setIsDelivering] = useState(false)
  const [openingLogin, setOpeningLogin] = useState(false)
  const [checkingLogin, setCheckingLogin] = useState(true)
  const [loginStateSource, setLoginStateSource] = useState('none')
  const [showLogoutDialog, setShowLogoutDialog] = useState(false)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [showLogoutResultDialog, setShowLogoutResultDialog] = useState(false)
  const [logoutResult, setLogoutResult] = useState<{ success: boolean; message: string } | null>(null)
  const [, setBackendAvailable] = useState(true)

  const [config, setConfig] = useState<ZhilianConfig>({
    keywords: '',
    cityCode: '',
    salary: '',
    dryRun: true,
    maxDeliveries: 1,
    stopOnCaptcha: true,
    stopOnRiskText: true,
    allowSimilarJobs: false,
    browserProfileMode: 'cookie_db',
  })
  const [options, setOptions] = useState<ZhilianOptions>({ city: [] })
  const [loadingConfig, setLoadingConfig] = useState(true)

  useEffect(() => {
    if (typeof window === 'undefined' || typeof EventSource === 'undefined') {
      console.warn('[智联招聘] EventSource 不可用，无法连接SSE')
      setCheckingLogin(false)
      return
    }

    const client = createSSEWithBackoff('http://localhost:8888/api/jobs/login-status/stream', {
      onError: (e, attempt, delay) => {
        console.warn(`[智联招聘 SSE] 连接错误，第${attempt}次重连，延迟 ${delay}ms`, e)
        setCheckingLogin(false)
      },
      listeners: [
        {
          name: 'connected',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              setIsLoggedIn(data.zhilianLoggedIn || false)
              setLoginStateSource(data.loginStateSources?.zhilian || 'none')
              setCheckingLogin(false)
            } catch (error) {
              console.error('[智联招聘 SSE] 解析连接消息失败:', error)
            }
          },
        },
        {
          name: 'login-status',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              if (data.platform === 'zhilian') {
                setIsLoggedIn(data.isLoggedIn)
                setLoginStateSource(data.loginStateSource || 'none')
                setCheckingLogin(false)
              }
            } catch (error) {
              console.error('[智联招聘 SSE] 解析登录状态消息失败:', error)
            }
          },
        },
        { name: 'ping', handler: () => {} },
      ],
    })

    return () => client.close()
  }, [])

  // 与猎聘一致的关键词解析/序列化
  const parseKeywordsFromDb = (raw?: string): string => {
    if (!raw) return ''
    const t = raw.trim()
    if (t.startsWith('[') && t.endsWith(']')) {
      try {
        const arr = JSON.parse(t)
        if (Array.isArray(arr)) return arr.filter(Boolean).join(', ')
      } catch (e) {
        console.warn('[智联] 解析关键词JSON失败，使用原值:', e)
      }
    }
    return t.replace(/，/g, ',')
  }

  const serializeKeywordsForDb = (display?: string): string => {
    const raw = (display || '').trim()
    if (!raw) return '[]'
    const norm = raw.replace(/，/g, ',')
    const tokens = norm
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0)
    return JSON.stringify(tokens)
  }

  const fetchAllData = async () => {
    try {
      const res = await fetch('http://localhost:8888/api/zhilian/config')
      const data = await res.json()
      if (data.config) {
        const normalized = { ...data.config }
        normalized.keywords = parseKeywordsFromDb(data.config.keywords)
        setConfig(withSafetyDefaults(normalized))
      }
      if (data.options) setOptions(data.options)
    } catch (e) {
      console.error('[智联] 获取配置失败:', e)
    } finally {
      setLoadingConfig(false)
    }
  }

  // 初次加载配置；后端可用性探测会按现有逻辑再次同步。
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { fetchAllData() }, [])

  // 探测后端可用性（与 51job 保持一致风格）
  useEffect(() => {
    (async () => {
      try {
        const res = await fetch('http://localhost:8888/api/zhilian/config', { method: 'GET' })
        const ok = !!res && res.ok
        setBackendAvailable(ok)
        if (ok) {
          await fetchAllData()
        } else {
          setLoadingConfig(false)
        }
      } catch {
        setBackendAvailable(false)
        setLoadingConfig(false)
      }
    })()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleStartDelivery = async () => {
    try {
      setIsDelivering(true)
      const response = await fetch('http://localhost:8888/api/zhilian/start', { method: 'POST' })
      const data = await response.json()
      if (!data.success) setIsDelivering(false)
    } catch {
      setIsDelivering(false)
    }
  }

  const handleOpenLogin = async () => {
    try {
      setOpeningLogin(true)
      const response = await fetch('http://localhost:8888/api/zhilian/login', { method: 'POST' })
      const data = await response.json()
      if (data.loginStateSource) setLoginStateSource(data.loginStateSource)
      if (!data.success) {
        console.warn('打开智联登录失败：', data.message)
      }
    } catch (error) {
      console.error('Failed to open Zhilian login:', error)
    } finally {
      setOpeningLogin(false)
      setCheckingLogin(false)
    }
  }

  const handleStopDelivery = async () => {
    try {
      const response = await fetch('http://localhost:8888/api/zhilian/stop', { method: 'POST' })
      const data = await response.json()
      if (data.success) setIsDelivering(false)
    } catch {}
  }

  const triggerLogout = async () => {
    try {
      const response = await fetch('http://localhost:8888/api/zhilian/logout', { method: 'POST' })
      const data = await response.json()
      setIsLoggedIn(false)
      setLogoutResult({ success: data.success, message: data.success ? '已退出登录，Cookie已清空。' : data.message })
      setShowLogoutResultDialog(true)
    } catch {
      setLogoutResult({ success: false, message: '退出登录失败：网络或服务异常。' })
      setShowLogoutResultDialog(true)
    }
  }

  const handleSaveConfig = async () => {
    try {
      const persistedConfig = { ...config } as ZhilianConfig & {
        minActionDelayMs?: number
        maxActionDelayMs?: number
        pauseEveryDeliveries?: number
        pauseSeconds?: number
      }
      delete persistedConfig.minActionDelayMs
      delete persistedConfig.maxActionDelayMs
      delete persistedConfig.pauseEveryDeliveries
      delete persistedConfig.pauseSeconds
      const payload = { ...persistedConfig, keywords: serializeKeywordsForDb(config.keywords) }
      const response = await fetch('http://localhost:8888/api/zhilian/config', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (response.ok) {
        try { await fetch('http://localhost:8888/api/cookie/save?platform=zhilian', { method: 'POST' }) } catch {}
        await fetchAllData()
        setSaveResult({ success: true, message: '保存成功，配置已更新。' })
      } else {
        setSaveResult({ success: false, message: '保存失败：后端返回异常状态。' })
      }
      setShowSaveDialog(true)
    } catch (error) {
      console.error('[智联] 保存配置失败:', error)
      setSaveResult({ success: false, message: '保存失败：网络或服务异常。' })
      setShowSaveDialog(true)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiBriefcase className="text-2xl" />}
        title="智联招聘配置"
        subtitle="配置智联招聘平台的求职参数"
        iconClass="text-white"
        accentBgClass="bg-purple-500"
        actions={
          <div className="flex items-center gap-2">
            <span className="rounded-full border border-white/30 bg-white/10 px-3 py-1 text-xs text-muted-foreground">
              {getDeliveryModeLabel(config)}
            </span>
            {checkingLogin ? (
              <Button size="sm" disabled className="rounded-full bg-gray-300 text-gray-600 cursor-not-allowed px-4 shadow">
                <BiPlay className="mr-1" /> 检查登录中...
              </Button>
            ) : !isLoggedIn ? (
              <Button onClick={handleOpenLogin} size="sm" disabled={openingLogin} className="rounded-full bg-gradient-to-r from-sky-500 to-cyan-500 hover:from-sky-600 hover:to-cyan-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105 disabled:opacity-60 disabled:hover:scale-100">
                <BiLogIn className="mr-1" /> {openingLogin ? '打开中...' : '打开智联登录'}
              </Button>
            ) : isDelivering ? (
              <Button onClick={handleStopDelivery} size="sm" className="rounded-full bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
                <BiStop className="mr-1" /> 停止投递
              </Button>
            ) : (
              <Button onClick={handleStartDelivery} size="sm" className="rounded-full bg-gradient-to-r from-teal-500 to-green-500 hover:from-teal-600 hover:to-green-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
                <BiPlay className="mr-1" /> 开始投递
              </Button>
            )}
            <Button onClick={() => setShowLogoutDialog(true)} size="sm" className="rounded-full bg-gradient-to-r from-red-500 to-pink-500 hover:from-red-600 hover:to-pink-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
              <BiLogOut className="mr-1" /> 退出登录
            </Button>
            <Button onClick={handleSaveConfig} size="sm" className="rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
              <BiSave className="mr-1" /> 保存配置
            </Button>
          </div>
        }
      />

      <Tabs defaultValue="config" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="config">平台配置</TabsTrigger>
          <TabsTrigger value="analytics">投递分析</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-6 mt-6">
          <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <BiBriefcase className="text-primary" />
                智联招聘平台说明
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <p className="text-sm text-muted-foreground">请在浏览器标签页中登录智联招聘平台，登录成功后系统会自动检测登录状态。</p>
                <p className="text-sm text-muted-foreground">登录成功后，点击“开始投递”按钮启动自动投递任务。</p>
                <p className="text-sm text-muted-foreground">点击“保存配置”按钮可手动保存当前登录相关信息到数据库。</p>
              </div>
            </CardContent>
          </Card>

          <SafetyControls
            config={config}
            onChange={(patch) => setConfig((prev) => ({ ...prev, ...patch }))}
            showSimilarJobs
            loginStateSource={loginStateSource}
          />

          {/* 配置表单 */}
          <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <BiBriefcase className="text-primary" />
                配置参数
              </CardTitle>
            </CardHeader>
            <CardContent>
              {loadingConfig ? (
                <p className="text-sm text-muted-foreground">配置加载中...</p>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>搜索关键词（逗号分隔）</Label>
                    <Input
                      placeholder="如：Java, 后端, Spring"
                      value={config.keywords || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, keywords: e.target.value }))}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>城市</Label>
                    <Select
                      value={config.cityCode || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, cityCode: e.target.value }))}
                      placeholder="请选择城市"
                    >
                      {options.city.map((o) => (
                        <option key={o.code} value={o.code}>{o.name}</option>
                      ))}
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label>薪资范围（最低和最高工资，用逗号分割）</Label>
                    <Input
                      placeholder="如：12000, 20000 或 不限"
                      value={config.salary || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, salary: e.target.value }))}
                    />
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="analytics" className="space-y-6 mt-6">
          <AnalysisContent />
        </TabsContent>
      </Tabs>

      {/* 退出确认弹框 */}
      {showLogoutDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <Card className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border-0">
            <CardHeader className="pb-2">
              <CardTitle className="text-lg flex items-center gap-2">
                <BiLogOut className="text-red-500" /> 确认退出登录
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground mb-4">退出后将清除Cookie并切换为未登录状态。</p>
              <div className="flex justify-end gap-2">
                <Button variant="ghost" onClick={() => setShowLogoutDialog(false)} className="rounded-full px-4">取消</Button>
                <Button onClick={async () => { await triggerLogout(); setShowLogoutDialog(false) }} className="rounded-full bg-gradient-to-r from-red-500 to-rose-600 text-white px-4">确认退出</Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* 退出登录结果弹框 */}
      {showLogoutResultDialog && logoutResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
          <Card className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border-0">
            <CardHeader className="pb-2">
              <CardTitle className="text-lg flex items-center gap-2">
                <BiLogOut className={logoutResult.success ? 'text-green-500' : 'text-red-500'} />
                {logoutResult.success ? '退出登录成功' : '退出登录失败'}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground mb-4">{logoutResult.message}</p>
              <Button onClick={() => setShowLogoutResultDialog(false)} className={`rounded-full px-4 ${logoutResult.success ? 'bg-green-500' : 'bg-red-500'} text-white`}>知道了</Button>
            </CardContent>
          </Card>
        </div>
      )}

      {/* 保存Cookie结果弹框 */}
      {showSaveDialog && saveResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
          <Card className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border-0">
            <CardHeader className="pb-2">
              <CardTitle className="text-lg flex items-center gap-2">
                <BiSave className={saveResult.success ? 'text-green-500' : 'text-red-500'} />
                {saveResult.success ? '保存成功' : '保存失败'}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground mb-4">{saveResult.message}</p>
              <Button onClick={() => setShowSaveDialog(false)} className={`rounded-full px-4 ${saveResult.success ? 'bg-green-500' : 'bg-red-500'} text-white`}>知道了</Button>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}
