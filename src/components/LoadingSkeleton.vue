<script setup>
defineProps({
  variant: { type: String, default: 'list', validator: (value) => ['list', 'detail', 'cards'].includes(value) },
  rows: { type: Number, default: 4 },
  label: { type: String, default: '正在加载' },
})
</script>

<template>
  <div class="ui-skeleton" :class="`ui-skeleton--${variant}`" role="status" :aria-label="label">
    <template v-if="variant === 'detail'">
      <span class="ui-skeleton-line title"></span>
      <span class="ui-skeleton-line medium"></span>
      <span v-for="row in rows" :key="row" class="ui-skeleton-block"></span>
    </template>
    <template v-else-if="variant === 'cards'">
      <span v-for="row in rows" :key="row" class="ui-skeleton-block"></span>
    </template>
    <template v-else>
      <span v-for="row in rows" :key="row" class="ui-skeleton-row">
        <span class="ui-skeleton-line medium"></span>
        <span class="ui-skeleton-line short"></span>
      </span>
    </template>
  </div>
</template>
