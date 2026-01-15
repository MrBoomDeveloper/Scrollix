<?php

use Illuminate\Support\Facades\Route;
use Inertia\Inertia;
use Laravel\Fortify\Features;

Route::get('/', function () {
    return Response::redirectTo("home/accepted");
})->name('main');

Route::get('home', function () {
    return Response::redirectTo("home/accepted");
})->name('home');

Route::get('home/accepted', function () {
    return Inertia::render('home/HomeAccepted');
})->name('home-accepted');

Route::get('home/not_decided', function () {
    return Inertia::render('home/HomeNotDecided');
})->name('home-not-decided');

Route::get('/home/not_accepted', function () {
    return Inertia::render('home/HomeNotAccepted');
})->name('home-not-accepted');

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
