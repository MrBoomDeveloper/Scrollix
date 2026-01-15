<script setup lang="ts">
import { Link, usePage } from '@inertiajs/vue3';

interface Props {
    selectedTab: "home" | "search" | "favourites" | "history" | "account"
}

withDefaults(defineProps<Props>(), {
    selectedTab: "home"
});

const page = usePage();
const user = page.props.auth.user;
</script>

<template>
    <Head>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin="true">
        <link href="https://fonts.googleapis.com/css2?family=Google+Sans+Flex:opsz,wght@6..144,1..1000&display=swap" rel="stylesheet">
    </Head>

    <div class="app">
        <div class="sidebar">
            <Link :href="'/'">
                <span class="logo">Scrollix</span>
            </Link>

            <Link :href="'/'">
                <div class="sidebar-item" :class="{'sidebar-item-selected': selectedTab == 'home'}">
                    <img :src="selectedTab == 'home' ? '/ic_home_filled.svg' : '/ic_home_outlined.svg'" />
                    <span>Home</span>
                </div>
            </Link>

            <Link :href="'/search'">
                <div class="sidebar-item" :class="{'sidebar-item-selected': selectedTab == 'search'}">
                    <img src="/ic_search.svg" />
                    <span>Search</span>
                </div>
            </Link>

            <Link :href="'/favourites'">
                <div class="sidebar-item" :class="{'sidebar-item-selected': selectedTab == 'favourites'}">
                    <img :src="selectedTab == 'favourites' ? '/ic_star_filled.svg' : '/ic_star_outlined.svg'" />
                    <span>Favourites</span>
                </div>
            </Link>

            <Link :href="'/history'">
                <div class="sidebar-item" :class="{'sidebar-item-selected': selectedTab == 'history'}">
                    <img src="/ic_history.svg" />
                    <span>History</span>
                </div>
            </Link>

            <span class="sidebar-spacing"></span>

            <Link :href="'/settings/profile'">
                <div class="sidebar-item" :class="{'sidebar-item-selected': selectedTab == 'account'}">
                    <img src="/ic_account_outlined.svg" />
                    <span>{{ user?.name ?? "Login" }}</span>
                </div>
            </Link>
        </div>

        <slot />
    </div>
</template>

<style scoped lang="scss">
    .app {
        width: 100vw;
        display: flex;
        background-color: rgb(11, 5, 18);
    }

    .logo {
        color: rgb(238, 131, 255);
        font-size: 1.4em;
        margin-bottom: .8rem;
        font-family: "Google Sans Flex", sans-serif;
        font-weight: 800;
    }

    .sidebar {
        position: sticky;
        height: 100vh;
        top: 0;
        display: flex;
        flex-direction: column;
        gap: .8rem;
        padding-block: 1rem;
        padding-inline: 1.4rem;
        background-color: rgb(19, 6, 34);

        .sidebar-spacing {
            flex-grow: 1;
        }

        .sidebar-item {
            display: flex;
            align-items: center;
            gap: .6rem;
            border-radius: .6rem;
            padding-block: .4rem;
            padding-inline: .6rem;
            min-width: 10rem;
            transition: .1s;

            img {
                width: 1.75rem;
            }

            span {
                font-weight: 300;
                font-family: "Google Sans Flex", sans-serif;
            }

            &-selected {
                background-color: rgb(238, 170, 255);
                color: black;

                img {
                    filter: brightness(0) saturate(100%);
                }

                span {
                    font-weight: 600;
                }
            }

            &:not(&-selected) {
                cursor: pointer;

                &:hover {
                    opacity: .8;
                }
            }
        }
    }
</style>