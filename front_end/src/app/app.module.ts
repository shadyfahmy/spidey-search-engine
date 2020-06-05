import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule, ReactiveFormsModule  } from "@angular/forms";
import { MatIconModule } from '@angular/material/icon';
import {MatCardModule} from '@angular/material/card';
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import { HttpClientModule } from '@angular/common/http';
import {MatToolbarModule} from '@angular/material/toolbar';
import { ResultsPageComponent } from './results-page/results-page.component';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatPaginatorModule} from '@angular/material/paginator';
import {ScrollingModule} from '@angular/cdk/scrolling';
import { ApiService } from './services/api.service';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';




@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    ResultsPageComponent
  ],
  imports: [
    BrowserModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatButtonModule,
    ScrollingModule,
    HttpClientModule,
    MatAutocompleteModule,
    AppRoutingModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatCardModule,
    FormsModule,
    ReactiveFormsModule,
    MatToolbarModule,
    BrowserAnimationsModule
  ],
  providers: [
    ApiService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
