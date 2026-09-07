<script setup>
defineProps({ fullName: String })
</script>

<template>
  <figure class="research-visual">
    <div class="research-visual-caption"><span>RESEARCH IN MOTION</span><span>空地协同研究</span></div>
    <svg class="research-diagram" viewBox="0 0 640 480" fill="none" role="img" aria-label="无人机在空中感知环境，与地面机器人协同的研究示意图">
      <g class="diagram-terrain" stroke-linejoin="round">
        <path d="m55 300 275-135 260 132-273 146Z" class="diagram-plane" />
        <path d="m110 273 262 141M165 246l262 139M220 219l261 137M275 192l260 135M110 328l272-136M166 356l272-136M220 386l274-137M275 413l273-138" class="diagram-grid" />
        <path d="m157 310 130-66 135 70-131 69Z" class="diagram-zone" />
        <path d="M306 159v153" class="diagram-connection" />
        <ellipse cx="306" cy="312" rx="40" ry="19" class="diagram-zone" />
        <path d="m188 326 80 41 119-62 91 46" class="diagram-route" />
        <circle cx="188" cy="326" r="5" class="diagram-point" />
        <circle cx="478" cy="351" r="5" class="diagram-point" />
      </g>
      <g class="diagram-drone" stroke-linecap="round" stroke-linejoin="round">
        <path d="m252 113 111 53M250 167l111-54" class="diagram-arm" />
        <ellipse cx="249" cy="109" rx="43" ry="16" class="diagram-prop" />
        <ellipse cx="365" cy="109" rx="43" ry="16" class="diagram-prop" />
        <ellipse cx="249" cy="168" rx="43" ry="16" class="diagram-prop" />
        <ellipse cx="365" cy="168" rx="43" ry="16" class="diagram-prop" />
        <path d="m282 130 24-12 26 13v24l-25 12-25-13Z" class="diagram-body" />
        <path d="m282 130 25 13 25-12M307 143v24" class="diagram-detail" />
        <circle cx="307" cy="153" r="3" class="diagram-point" />
      </g>
      <g class="diagram-robot" stroke-linecap="round" stroke-linejoin="round">
        <path d="m406 292-5 29 10 10M449 312l-2 26 10 9M459 286l10 20-3 24M425 274l-2 24-9 12" class="diagram-arm" />
        <path d="m399 281 32-16 38 21v22l-32 16-38-22Z" class="diagram-body" />
        <path d="m399 281 38 21 32-16M437 302v22" class="diagram-detail" />
        <path d="m459 281 17-7 11 8v16l-16 8-12-7Z" class="diagram-body" />
        <circle cx="480" cy="287" r="3" class="diagram-point" />
      </g>
      <g class="diagram-annotation">
        <path d="M409 107h61l16-17M480 325h46l16-17M185 328h-62l-16 17" class="diagram-grid" />
        <text x="478" y="78">自主飞行</text><text x="501" y="294">地面协同</text><text x="54" y="369">环境感知</text>
      </g>
    </svg>
    <figcaption class="research-visual-footer"><span>{{ fullName || 'Yichun Embodied Science' }}</span><span>研究示意 / CONCEPT</span></figcaption>
  </figure>
</template>
