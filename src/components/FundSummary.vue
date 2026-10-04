<script setup>
import { formatMoney } from '../services/fundFormat'
defineProps({ summary: { type: Object, default: null }, opening: Boolean })
</script>
<template>
  <div class="fund-totals">
    <div class="fund-balance">
      <span>基金余额</span><strong>{{ formatMoney(summary?.balance) }}</strong>
    </div>
    <div>
      <span>累计收入</span><strong>{{ formatMoney(summary?.income) }}</strong>
    </div>
    <div>
      <span>累计支出</span><strong>{{ formatMoney(summary?.expense) }}</strong>
    </div>
    <div v-if="opening">
      <span>期初余额</span><strong>{{ formatMoney(summary?.openingBalance) }}</strong>
    </div>
  </div>
</template>
