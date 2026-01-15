<?php

use Illuminate\Support\Facades\Route;
use Inertia\Inertia;
use Laravel\Fortify\Features;

Route::get('/', function () {
    return Inertia::render('Home');
})->name('home');

Route::get('search', function () {
    return Inertia::render('Search');
})->name('search');

Route::get('favourites', function () {
    return Inertia::render('Favourites');
})->name('favourites');

Route::get('history', function () {
    return Inertia::render('History');
})->name('history');

require __DIR__.'/settings.php';
