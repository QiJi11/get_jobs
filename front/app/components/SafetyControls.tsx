'use client'

import { BiStop } from 'react-icons/bi'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export interface SafetyConfigValues {
  dryRun?: boolean
  maxDeliveries?: number
  stopOnCaptcha?: boolean
  stopOnRiskText?: boolean
  allowSimilarJobs?: boolean
}

interface SafetyControlsProps {
  config: SafetyConfigValues
  onChange: (patch: Partial<SafetyConfigValues>) => void
  showSimilarJobs?: boolean
}

type NormalizedSafetyConfigValues = Required<SafetyConfigValues>

/**
 * 为安全配置补齐保守默认值。
 */
export function withSafetyDefaults<T extends SafetyConfigValues>(config: T): T & NormalizedSafetyConfigValues {
  return {
    ...config,
    dryRun: config.dryRun ?? true,
    maxDeliveries: config.maxDeliveries ?? 1,
    stopOnCaptcha: config.stopOnCaptcha ?? true,
    stopOnRiskText: config.stopOnRiskText ?? true,
    allowSimilarJobs: config.allowSimilarJobs ?? false,
  }
}

/**
 * 返回开始投递按钮旁展示的当前模式文案。
 */
export function getDeliveryModeLabel(config: SafetyConfigValues): string {
  const safe = withSafetyDefaults(config)
  return safe.dryRun ? 'Dry-run' : `真实投递，最多 ${safe.maxDeliveries} 条`
}

/**
 * 渲染平台通用安全控制项。
 */
export default function SafetyControls({ config, onChange, showSimilarJobs = false }: SafetyControlsProps) {
  const safe = withSafetyDefaults(config)
  const updateBool = (key: keyof SafetyConfigValues, checked: boolean) => onChange({ [key]: checked } as Partial<SafetyConfigValues>)

  return (
    <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <BiStop className="text-primary" />
          安全控制
        </CardTitle>
        <CardDescription>默认先审计候选，真实投递必须显式关闭 Dry-run。</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <label className="flex items-start gap-3 rounded-lg border border-white/20 bg-white/5 p-3">
            <input
              type="checkbox"
              checked={safe.dryRun}
              onChange={(event) => updateBool('dryRun', event.target.checked)}
              className="mt-1 h-4 w-4"
            />
            <span>
              <span className="block text-sm font-medium">Dry-run</span>
              <span className="block text-xs text-muted-foreground">只记录候选和安全日志，不点击投递。</span>
            </span>
          </label>

          <div className="space-y-2">
            <Label htmlFor="maxDeliveries">最大真实投递数</Label>
            <Input
              id="maxDeliveries"
              type="number"
              min={1}
              value={safe.maxDeliveries}
              onChange={(event) => {
                const value = Number(event.target.value)
                onChange({ maxDeliveries: Number.isFinite(value) && value > 0 ? value : 1 })
              }}
            />
          </div>

          <label className="flex items-start gap-3 rounded-lg border border-white/20 bg-white/5 p-3">
            <input
              type="checkbox"
              checked={safe.stopOnCaptcha}
              onChange={(event) => updateBool('stopOnCaptcha', event.target.checked)}
              className="mt-1 h-4 w-4"
            />
            <span>
              <span className="block text-sm font-medium">验证码停止</span>
              <span className="block text-xs text-muted-foreground">命中验证码、滑块或访问验证后停止。</span>
            </span>
          </label>

          <label className="flex items-start gap-3 rounded-lg border border-white/20 bg-white/5 p-3">
            <input
              type="checkbox"
              checked={safe.stopOnRiskText}
              onChange={(event) => updateBool('stopOnRiskText', event.target.checked)}
              className="mt-1 h-4 w-4"
            />
            <span>
              <span className="block text-sm font-medium">风控停止</span>
              <span className="block text-xs text-muted-foreground">命中频控、上限或异常访问文案后停止。</span>
            </span>
          </label>
        </div>

        {showSimilarJobs && (
          <div className="mt-4 rounded-lg border border-red-300/60 bg-red-500/10 p-3">
            <label className="flex items-start gap-3">
              <input
                type="checkbox"
                checked={safe.allowSimilarJobs}
                onChange={(event) => updateBool('allowSimilarJobs', event.target.checked)}
                className="mt-1 h-4 w-4"
              />
              <span>
                <span className="block text-sm font-semibold text-red-700 dark:text-red-300">允许相似职位投递</span>
                <span className="block text-xs text-red-700/80 dark:text-red-200/80">开启后可能触发批量相似岗位投递。</span>
              </span>
            </label>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
