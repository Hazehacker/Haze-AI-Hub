<template>
  <div class="loading-spinner">
    <el-icon class="is-loading">
      <Loading />
    </el-icon>
  </div>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'
</script>

<style scoped>
.loading-spinner {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 2rem 0;
}

.loading-spinner .el-icon {
  font-size: 2rem;
  color: var(--el-color-primary);
}
</style>

