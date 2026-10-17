import { demoApi } from './demo.js'
import { liveApi } from './live.js'

export function getApi(mode) {
  return mode === 'live' ? liveApi : demoApi
}
