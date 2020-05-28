import { Component, OnInit } from '@angular/core';
import {FormControl} from '@angular/forms';
import {Observable} from 'rxjs';
import { Routes, RouterModule, Router, ActivatedRoute } from '@angular/router';
import {map, startWith} from 'rxjs/operators';

import { ApiService } from '../services/api.service';
const { webkitSpeechRecognition }: IWindow = (window as any) as IWindow;
@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {

  control = new FormControl();
  suggestions: any[];
  suggestionsTxt: string[] = [];
  value = "";
  listening = false;
  night = false;
  
  recognition = new webkitSpeechRecognition;

  constructor( private route: ActivatedRoute, private router: Router,
     private apiService: ApiService ) { }

  ngOnInit() {
    this.suggestions = null;

    //localStorage.removeItem('id')

    let user = localStorage.getItem('id')
    if (user != null) {
      console.log("data from local storage = " + user)
    }
    else{
      this.apiService.addUser().subscribe(data => {
        console.log(data);
        localStorage.setItem('id',  data[0].id)
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

  }

  Suggestions(text : string) {
    this.apiService.getSuggestions(text).subscribe(data => {
      this.suggestions = data._embedded.queries;
      this.suggestionsTxt = [];
      for(let s of this.suggestions) {
        this.suggestionsTxt.push(s.text)
      }
    })
  }

  search() {
    if(this.value.replace(/\s/g, '') != ""){
      this.apiService.saveQuery(this.value).subscribe(data => {
      });
      this.router.navigate(['search', this.value, "false", 1])
    }
  }

  imSearch() {
    if(this.value.replace(/\s/g, '') != ""){
      this.apiService.saveQuery(this.value).subscribe(data => {
      });
      this.router.navigate(['search', this.value, "true", 1])
    }  
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

}
export interface IWindow extends Window {
  webkitSpeechRecognition: any;
}