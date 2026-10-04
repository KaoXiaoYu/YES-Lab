<script setup>
import { ArrowUpRight } from '@lucide/vue'
import { brand, repositoryLabel } from '../config/site'
</script>

<template>
  <footer class="repository-footer">
    <span>{{ brand.name }} · 实验室协作平台</span>
    <a
      :href="brand.repositoryUrl"
      target="_blank"
      rel="noopener noreferrer"
      aria-label="项目仓库（GitHub，在新窗口打开）"
    >
      {{ repositoryLabel }} <ArrowUpRight :size="16" aria-hidden="true" />
    </a>
  </footer>
</template>
