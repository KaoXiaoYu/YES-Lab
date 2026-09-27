<script setup>
import MelinaMascot from './MelinaMascot.vue'
import nailongArtwork from '../assets/nailong-laughing-animated.svg'
import nailongStaticArtwork from '../assets/nailong-laughing.svg'

defineProps({ mascot: { type: String, default: 'MELINA' } })
</script>

<template>
  <picture v-if="mascot === 'NAILONG'" class="nailong-mascot">
    <source media="(prefers-reduced-motion: reduce)" :srcset="nailongStaticArtwork" />
    <img :src="nailongArtwork" alt="奶龙捧腹大笑" width="88" height="124" />
  </picture>
  <MelinaMascot v-else />
</template>

<style scoped>
.nailong-mascot {
  display: block;
  flex: 0 0 auto;
  width: 88px;
  height: 124px;
}
.nailong-mascot img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
</style>
