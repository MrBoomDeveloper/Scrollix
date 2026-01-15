<script setup lang="ts">
import AppWrapper from '@/layouts/AppWrapper.vue';
import { Head } from '@inertiajs/vue3';
</script>

<template>
    <Head title="Favourites - Scrollix" />

    <AppWrapper :selectedTab="'favourites'">
        <h1>Favourites screen</h1>
    </AppWrapper>
</template>

<style scoped lang="scss">

</style>