import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import {PageEvent} from '@angular/material/paginator';
import { Observable } from 'rxjs';
import { startWith, map } from 'rxjs/operators';
import { Result } from '../common/result';
import { Routes, RouterModule, Router, ActivatedRoute } from '@angular/router';
import { ApiService } from '../services/api.service';
import { IWindow } from '../home/home.component'
const { webkitSpeechRecognition }: IWindow = (window as any) as IWindow;

import { AppComponent } from '../app.component';

@Component({
  selector: 'app-results-page',
  templateUrl: './results-page.component.html',
  styleUrls: ['./results-page.component.scss']
})
export class ResultsPageComponent implements OnInit {
  value = ""
  control = new FormControl();
  suggestions: any[];
  suggestionsTxt: string[] = [];
  results: Result[];
  night = false;
  images: string[];
  listening = false;
  loading = true;
  imageSearch = false;
  page: number;
  next = false;
  recognition = new webkitSpeechRecognition;

  constructor(private apiService: ApiService,
      private route: ActivatedRoute,
      private router: Router) {

      this.route.params.subscribe( params => {this.value = params.query;
      this.page = parseInt(params.page);
      if(params.im == "true")
        this.imageSearch = true;
      else
        this.imageSearch = false;
    } )  
  }

  ngOnInit() {
    this.loading = true;
    this.results = null
    this.images = null
    //localStorage.removeItem('id')

    let user = localStorage.getItem('id')
    if (user != null) {
      console.log("data from local storage = " + user)
    }
    else{
      this.apiService.addUser().subscribe(data => {
        console.log(data);
        localStorage.setItem('id', data[0].id)
        console.log("data saved to local storage = " +  localStorage.getItem('id'))
      });
    }

    let nm = localStorage.getItem('nightMode')
    if (nm != null) {
      console.log("data from local storage = " + nm)
      if(nm == "true")
        this.night=true;
      else
        this.night=false;
    }
    else{
      localStorage.setItem('nightMode', "false")
      console.log("data saved to local storage = " +  localStorage.getItem('nightMode'))
    }

    if (typeof webkitSpeechRecognition === "undefined") {
      console.log("error")
    } else {
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.addEventListener("result", this.onResult);
    }

    this.suggestions = null;

    if(this.imageSearch) {
      this.apiService.getImages(this.value,this.page,localStorage.getItem('id')).subscribe(data => {
        this.results = null
        this.images = null
        if(data){
          if(data.length > 0){
            this.images = data;
            if(this.images.length < 20)
              this.next = false;
            else
              this.next = true
          }
          console.log(this.images)
          this.next = false;
          this.loading = false
        }      
        else {
          this.next = false;
          this.loading = false
        }
      })
    }
    else {
      this.apiService.getResults(this.value,this.page,localStorage.getItem('id')).subscribe(data => {
        this.results = null
        this.images = null

        if(data != null) {
          if(data.length > 0 ){
            this.results = data;
            if(this.results.length < 20)
              this.next = false;
            else
              this.next = true
          }
          console.log(this.results)
          this.next = false;
          this.loading = false
        }
        else {
          this.next = false;
          this.loading = false
        }
      
      })
    }

  }

  Search() {
    console.log("preshit")
    if(this.value.replace(/\s/g, '') != ""){
      console.log("shit")
      this.imageSearch = false;
      this.apiService.saveQuery(this.value).subscribe(data => {
      });
      this.router.navigate(['search', this.value, "false", 1])
      this.ngOnInit()
    }
  }

  ImageSearch() {
    if(this.value.replace(/\s/g, '') != ""){
      this.imageSearch = true;
      this.apiService.saveQuery(this.value).subscribe(data => {
      });
      this.router.navigate(['search', this.value, "true", 1])
      this.ngOnInit()
    }
  }

  Suggestions(text : string) {
    this.apiService.getSuggestions(text).subscribe(data => {
      this.suggestions = data._embedded.queries;
      this.suggestionsTxt = []
      for(let s of this.suggestions) {
        this.suggestionsTxt.push(s.text)
      }
    })
  }

  listen = () => {
    if(!this.listening) {
      console.log("start listening")
      this.listening = true;
      this.recognition.start();
    }
    else{
      console.log("stop listening")
      this.listening = false;
      this.recognition.stop();
    }
  };

  onResult = event => {
    let x =""
    for (const res of event.results) {
      const text = document.createTextNode(res[0].transcript);

      x = x + text.textContent;
    }
    console.log(x);
    this.value = x;
  };

  nightMode() {
    this.night = !this.night;
    if(this.night)
      localStorage.setItem('nightMode', "true")
    else
      localStorage.setItem('nightMode', "false")
  }

  history(url_id) {
    this.apiService.addHistory(localStorage.getItem('id'), url_id).subscribe(data => {
    })
  }

  nextPage() {
    this.page = this.page + 1;
    if(this.imageSearch) {
      this.router.navigate(['search', this.value, "true", this.page])
    }
    else{
      this.router.navigate(['search', this.value, "false", this.page])

    }
  }
  
  prevPage() {
    if(this.page >=2 ) {
    this.page = this.page - 1;
    if(this.imageSearch) {
      this.router.navigate(['search', this.value, "true", this.page])
    }
    else{
      this.router.navigate(['search', this.value, "false", this.page])

    }
  }

}
}
