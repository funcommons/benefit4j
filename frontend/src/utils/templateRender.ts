import type { PromptVar } from '@/api/types'

/**
 * 把 promptTemplate 中的 {var} 替换为 values 中的值
 * 缺失或空值的占位符保留为 {name}, 提示用户有必填字段未填
 *
 * 安全: 不使用 eval / new Function, 仅做字符串替换
 */
export function renderPrompt(template: string, values: Record<string, unknown>): string {
  return template.replace(/\{(\w+)\}/g, (m, key) => {
    const v = values[key]
    if (v == null || v === '') return m
    return String(v)
  })
}

/**
 * 校验必填字段, 返回未填的字段 label 列表 (中文, 可直接给用户看)
 */
export function validateRequired(
  vars: PromptVar[],
  values: Record<string, unknown>
): string[] {
  return vars
    .filter(v => v.required && (values[v.name] == null || values[v.name] === ''))
    .map(v => v.label)
}

/**
 * 用 vars.default 初始化 formValues
 * switch 类型默认 false, 其它用 var.default
 */
export function buildInitialValues(vars: PromptVar[]): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  vars.forEach(v => {
    if (v.type === 'switch') {
      out[v.name] = v.default ?? false
    } else {
      out[v.name] = v.default ?? ''
    }
  })
  return out
}
