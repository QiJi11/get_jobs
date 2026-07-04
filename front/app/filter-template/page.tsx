'use client'

import { useEffect, useState } from 'react'
import { BiSave, BiRefresh, BiSliderAlt } from 'react-icons/bi'
import PageHeader from '@/app/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

interface FilterTemplate {
  id?: number
  name?: string
  keywords?: string
  city?: string
  salary?: string
  degree?: string
  experience?: string
  companyScale?: string
  industry?: string
  jobExcludeWords?: string
  companyBlacklist?: string
}

type ApplyResult = Record<string, { applied?: string[]; unsupported?: string[] }>

const emptyTemplate: FilterTemplate = {
  name: '默认筛选模板',
  keywords: '',
  city: '',
  salary: '',
  degree: '',
  experience: '',
  companyScale: '',
  industry: '',
  jobExcludeWords: '',
  companyBlacklist: '',
}

export default function FilterTemplatePage() {
  const [template, setTemplate] = useState<FilterTemplate>(emptyTemplate)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [applying, setApplying] = useState(false)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [message, setMessage] = useState('')

  const update = (patch: Partial<FilterTemplate>) => setTemplate((prev) => ({ ...prev, ...patch }))

  const loadTemplate = async () => {
    try {
      setLoading(true)
      const response = await fetch('http://localhost:8888/api/filter-template')
      const data = await response.json()
      setTemplate({ ...emptyTemplate, ...data })
    } catch {
      setMessage('统一筛选模板加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadTemplate()
  }, [])

  const saveTemplate = async () => {
    try {
      setSaving(true)
      const response = await fetch('http://localhost:8888/api/filter-template', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(template),
      })
      const data = await response.json()
      setTemplate({ ...emptyTemplate, ...data })
      setMessage('已保存')
    } catch {
      setMessage('保存失败')
    } finally {
      setSaving(false)
    }
  }

  const applyTemplate = async () => {
    try {
      setApplying(true)
      const response = await fetch('http://localhost:8888/api/filter-template/apply', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(template),
      })
      const data = await response.json()
      setResult(data.result || null)
      setMessage('已应用到平台配置')
    } catch {
      setMessage('应用失败')
    } finally {
      setApplying(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-cyan-50 dark:from-blacksection dark:via-blackho dark:to-blacksection">
      <PageHeader
        title="统一筛选"
        subtitle="跨平台筛选模板"
        icon={<BiSliderAlt />}
        actions={
          <div className="flex items-center gap-2">
            <Button onClick={loadTemplate} disabled={loading} size="sm" className="rounded-full bg-white/80 text-slate-700 hover:bg-white shadow">
              <BiRefresh className="mr-1" /> 刷新
            </Button>
            <Button onClick={saveTemplate} disabled={saving} size="sm" className="rounded-full bg-gradient-to-r from-cyan-500 to-blue-500 text-white shadow">
              <BiSave className="mr-1" /> 保存
            </Button>
            <Button onClick={applyTemplate} disabled={applying} size="sm" className="rounded-full bg-gradient-to-r from-emerald-500 to-teal-500 text-white shadow">
              <BiSliderAlt className="mr-1" /> 应用到四个平台
            </Button>
          </div>
        }
      />

      <div className="max-w-6xl mx-auto p-6 space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>模板字段</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label htmlFor="templateName">名称</Label>
                <Input id="templateName" value={template.name || ''} onChange={(event) => update({ name: event.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="city">城市</Label>
                <Input id="city" value={template.city || ''} onChange={(event) => update({ city: event.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="salary">薪资</Label>
                <Input id="salary" value={template.salary || ''} onChange={(event) => update({ salary: event.target.value })} />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="keywords">关键词</Label>
                <Textarea id="keywords" rows={4} value={template.keywords || ''} onChange={(event) => update({ keywords: event.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="industry">行业</Label>
                <Textarea id="industry" rows={4} value={template.industry || ''} onChange={(event) => update({ industry: event.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="experience">经验</Label>
                <Textarea id="experience" rows={4} value={template.experience || ''} onChange={(event) => update({ experience: event.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="degree">学历</Label>
                <Textarea id="degree" rows={4} value={template.degree || ''} onChange={(event) => update({ degree: event.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="companyScale">公司规模</Label>
                <Textarea id="companyScale" rows={4} value={template.companyScale || ''} onChange={(event) => update({ companyScale: event.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="jobExcludeWords">职位排除词</Label>
                <Textarea id="jobExcludeWords" rows={4} value={template.jobExcludeWords || ''} onChange={(event) => update({ jobExcludeWords: event.target.value })} />
              </div>
              <div className="space-y-2 md:col-span-2">
                <Label htmlFor="companyBlacklist">公司黑名单</Label>
                <Textarea id="companyBlacklist" rows={4} value={template.companyBlacklist || ''} onChange={(event) => update({ companyBlacklist: event.target.value })} />
              </div>
            </div>
          </CardContent>
        </Card>

        {(message || result) && (
          <Card>
            <CardHeader>
              <CardTitle>结果</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {message && <div className="text-sm text-muted-foreground">{message}</div>}
              {result && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {Object.entries(result).map(([platform, value]) => (
                    <div key={platform} className="rounded-lg border border-white/20 bg-white/5 p-3">
                      <div className="font-medium">{platform}</div>
                      <div className="mt-2 text-sm text-muted-foreground">applied: {(value.applied || []).join(', ') || '-'}</div>
                      <div className="text-sm text-muted-foreground">unsupported: {(value.unsupported || []).join(', ') || '-'}</div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  )
}
