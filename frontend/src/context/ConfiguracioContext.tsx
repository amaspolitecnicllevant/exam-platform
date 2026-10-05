import { createContext, useContext, useEffect, useState, ReactNode } from 'react'
import { getConfiguracio, type ConfiguracioDto } from '../api/configuracio'

interface ConfiguracioContextValue {
  config: ConfiguracioDto | null
  reload: () => void
}

const DEFAULT_CONFIG: ConfiguracioDto = {
  nomCentre: "Plataforma d'Avaluació",
  hasLogo: false,
  colorMarca: '#b0306a',
  cursActiu: '',
  duradaDefecte: 90,
  penalitzacioDefecte: 0.25,
  focusLossThreshold: 5,
  gracePeriodSeconds: 30,
  dominisOauth: '',
  copiesLlindar: 80,
  copiesLlindarApunts: 95,
  googleActiu: false,
}

const ConfiguracioContext = createContext<ConfiguracioContextValue>({
  config: DEFAULT_CONFIG,
  reload: () => {},
})

function hexToHsl(hex: string): { h: number; s: number } | null {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  if (!result) return null
  const r = parseInt(result[1], 16) / 255
  const g = parseInt(result[2], 16) / 255
  const b = parseInt(result[3], 16) / 255
  const max = Math.max(r, g, b), min = Math.min(r, g, b)
  const l = (max + min) / 2
  if (max === min) return { h: 0, s: 0 }
  const d = max - min
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
  let h = 0
  if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6
  else if (max === g) h = ((b - r) / d + 2) / 6
  else h = ((r - g) / d + 4) / 6
  return { h: Math.round(h * 360), s: Math.round(s * 100) }
}

function applyColorMarca(hex: string) {
  const hsl = hexToHsl(hex)
  if (!hsl) return
  document.documentElement.style.setProperty('--brand-h', String(hsl.h))
  document.documentElement.style.setProperty('--brand-s', `${hsl.s}%`)
}

export function ConfiguracioProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<ConfiguracioDto | null>(null)

  const load = () => {
    getConfiguracio()
      .then(c => {
        setConfig(c)
        if (c.colorMarca) applyColorMarca(c.colorMarca)
      })
      .catch(() => setConfig(DEFAULT_CONFIG))
  }

  useEffect(() => { load() }, [])

  return (
    <ConfiguracioContext.Provider value={{ config, reload: load }}>
      {children}
    </ConfiguracioContext.Provider>
  )
}

export function useConfiguracio() {
  return useContext(ConfiguracioContext)
}

/** Pèrdues de focus a partir de les quals un alumne es marca al monitor i a la correcció. */
export function useLlindarFocus(): number {
  const { config } = useContext(ConfiguracioContext)
  return config?.focusLossThreshold ?? DEFAULT_CONFIG.focusLossThreshold
}
