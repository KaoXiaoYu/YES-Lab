<script setup>
import { Bot, Cpu, ScanLine } from 'lucide-vue-next'
defineProps({ fullName: String })
</script>

<template>
  <div class="research-visual">
    <div class="research-visual-caption" aria-hidden="true"><span>YES / RESEARCH FIELD</span><ScanLine :size="18" /></div>
    <div class="research-orbit" aria-hidden="true">
      <svg class="research-orbit-lines" viewBox="0 0 500 440" fill="none">
        <ellipse cx="250" cy="220" rx="190" ry="150" />
        <ellipse cx="250" cy="220" rx="140" ry="110" />
        <path d="M40 220H460M250 35V405M105 90L395 350M105 350L395 90" />
        <circle cx="250" cy="220" r="185" class="research-orbit-dashes" />
      </svg>
      <span class="research-node research-node-air"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="m7 7 10 10M7 17 17 7" /><circle cx="6" cy="6" r="4" /><circle cx="18" cy="6" r="4" /><circle cx="6" cy="18" r="4" /><circle cx="18" cy="18" r="4" /><rect x="9" y="9" width="6" height="6" rx="2" fill="currentColor" /></svg><span>AIR<span>自主飞行</span></span></span>
      <span class="research-node research-node-ground"><Bot :size="28" /><span>GROUND<span>空地协同</span></span></span>
      <span class="research-node research-node-ai"><Cpu :size="24" /><span>INTELLIGENCE<span>具身智能</span></span></span>
    </div>
    <div class="research-logo"><img src="/yes-lab-logo.png" alt="YES Lab，Y 为蓝色、E 为黄色、S 为红色" width="900" height="506" /></div>
    <div class="research-visual-footer"><span>{{ fullName || 'Yichun Embodied Science' }}</span><span aria-hidden="true">Y / E / S <i></i></span></div>
  </div>
</template>
